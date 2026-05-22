package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.proxyservice.key.dto.InternalFingerprintLookupRequest;
import com.eevee.proxyservice.key.dto.InternalFingerprintLookupResponse;
import com.eevee.usage.events.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Parallel POST fingerprint lookup against identity-service and team-service.
 */
@Service
public class FingerprintReverseLookupService {

    private static final Logger log = LoggerFactory.getLogger(FingerprintReverseLookupService.class);
    private static final String LOOKUP_PATH = "/internal/v1/api-keys/lookup";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ProxyProperties proxyProperties;
    private final WebClient identityClient;
    private final WebClient teamClient;
    private final String internalToken;

    public FingerprintReverseLookupService(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
        this.internalToken = resolveInternalToken(proxyProperties);
        this.identityClient = WebClient.builder()
                .baseUrl(proxyProperties.getKeyService().getBaseUrl())
                .build();
        this.teamClient = WebClient.builder()
                .baseUrl(proxyProperties.getTeamKeyService().getBaseUrl())
                .build();
    }

    public Mono<FingerprintOwnerLookup> lookup(AiProvider provider, String fingerprint64, String correlationId) {
        String prefix = ApiKeyFingerprintNormalizer.fingerprintPrefix(
                fingerprint64,
                fingerprintLogPrefixLength()
        );
        Mono<FingerprintLookupAttempt> identity = postLookup(identityClient, "identity-service", provider, fingerprint64);
        Mono<FingerprintLookupAttempt> team = postLookup(teamClient, "team-service", provider, fingerprint64);
        return Mono.zip(identity, team)
                .map(tuple -> {
                    FingerprintOwnerLookup owner = FingerprintLookupMergePolicy.merge(
                            tuple.getT1(),
                            tuple.getT2(),
                            prefix
                    );
                    log.info(
                            "fingerprint lookup resolved ownerType={} fingerprintPrefix={} correlationId={} keyId={}",
                            owner.ownerType(),
                            prefix,
                            correlationId != null ? correlationId : "-",
                            masked(owner.keyId())
                    );
                    return owner;
                });
    }

    private int fingerprintLogPrefixLength() {
        return proxyProperties.getFingerprintLookup().getFingerprintLogPrefixLength();
    }

    private Mono<FingerprintLookupAttempt> postLookup(
            WebClient client,
            String lookupTarget,
            AiProvider provider,
            String fingerprint64
    ) {
        InternalFingerprintLookupRequest body = new InternalFingerprintLookupRequest(fingerprint64, provider.name());
        return client.post()
                .uri(LOOKUP_PATH)
                .headers(headers -> {
                    if (StringUtils.hasText(internalToken)) {
                        headers.setBearerAuth(internalToken);
                    }
                })
                .bodyValue(body)
                .exchangeToMono(response -> classifyResponse(response, lookupTarget))
                .timeout(REQUEST_TIMEOUT)
                .onErrorResume(WebClientResponseException.class, ex -> Mono.just(classifyException(ex)))
                .onErrorResume(WebClientRequestException.class, ex -> {
                    log.warn("fingerprint lookup connection failed lookupTarget={} cause={}", lookupTarget, ex.toString());
                    return Mono.just(FingerprintLookupAttempt.gatewayError(502));
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("fingerprint lookup failed lookupTarget={} cause={}", lookupTarget, ex.toString());
                    return Mono.just(FingerprintLookupAttempt.gatewayError(502));
                });
    }

    private Mono<FingerprintLookupAttempt> classifyResponse(
            org.springframework.web.reactive.function.client.ClientResponse response,
            String lookupTarget
    ) {
        HttpStatusCode status = response.statusCode();
        if (status.is2xxSuccessful()) {
            return response.bodyToMono(InternalFingerprintLookupResponse.class)
                    .map(FingerprintLookupAttempt::found);
        }
        return response.releaseBody().then(Mono.fromSupplier(() -> classifyStatus(status.value(), lookupTarget)));
    }

    private static FingerprintLookupAttempt classifyStatus(int status, String lookupTarget) {
        return switch (status) {
            case 404 -> FingerprintLookupAttempt.notFound();
            case 409 -> FingerprintLookupAttempt.conflict();
            case 400 -> FingerprintLookupAttempt.badRequest();
            case 403 -> FingerprintLookupAttempt.gatewayError(403);
            default -> {
                if (status >= 500) {
                    yield FingerprintLookupAttempt.gatewayError(status);
                }
                yield FingerprintLookupAttempt.gatewayError(502);
            }
        };
    }

    private static FingerprintLookupAttempt classifyException(WebClientResponseException ex) {
        return classifyStatus(ex.getStatusCode().value(), "unknown");
    }

    static String resolveInternalToken(ProxyProperties proxyProperties) {
        ProxyProperties.FingerprintLookup cfg = proxyProperties.getFingerprintLookup();
        if (StringUtils.hasText(cfg.getInternalToken())) {
            return cfg.getInternalToken().trim();
        }
        if (StringUtils.hasText(proxyProperties.getKeyService().getInternalToken())) {
            return proxyProperties.getKeyService().getInternalToken().trim();
        }
        return proxyProperties.getTeamKeyService().getInternalToken().trim();
    }

    private static String masked(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        int keep = Math.min(4, value.length());
        return value.substring(0, keep) + "***";
    }
}
