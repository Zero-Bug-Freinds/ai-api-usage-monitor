package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.proxyservice.key.dto.TeamKeyCredentialResponse;
import com.eevee.usage.events.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Loads decrypted team API keys for ext fingerprint relay (trusted internal API only).
 */
@Service
public class TeamKeyCredentialClient {

    private static final Logger log = LoggerFactory.getLogger(TeamKeyCredentialClient.class);

    private final ProxyProperties proxyProperties;
    private final WebClient teamKeyServiceWebClient;

    public TeamKeyCredentialClient(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
        this.teamKeyServiceWebClient = WebClient.builder()
                .baseUrl(proxyProperties.getTeamKeyService().getBaseUrl())
                .build();
    }

    /**
     * @param teamId team PK string from fingerprint lookup
     * @param keyId  team API key row id from fingerprint lookup
     */
    public TeamKeyCredentialResponse loadCredential(String teamId, String keyId, AiProvider provider) {
        if (!hasText(teamId) || !hasText(keyId)) {
            throw new ResponseStatusException(BAD_GATEWAY, "team fingerprint lookup missing teamId or keyId");
        }
        ProxyProperties.TeamKeyService teamKeyService = proxyProperties.getTeamKeyService();
        String token = teamKeyService.getInternalToken();
        String pathTemplate = teamKeyService.getCredentialPathTemplate();
        WebClientResponseException last404 = null;
        for (String providerSegment : ApiKeyClient.providerSegmentsForLookup(provider)) {
            try {
                TeamKeyCredentialResponse body = loadCredentialByProviderSegment(
                        teamId,
                        keyId,
                        providerSegment,
                        token,
                        pathTemplate
                );
                if (body == null || !hasText(body.plainKey())) {
                    throw new ResponseStatusException(BAD_GATEWAY, "team credential lookup returned empty key");
                }
                log.info(
                        "team credential lookup success teamId={} provider={} keyId={}",
                        masked(teamId),
                        provider.pathSegment(),
                        masked(keyId)
                );
                return body;
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 404) {
                    last404 = e;
                    continue;
                }
                log.warn(
                        "team credential lookup failed teamId={} provider={} keyId={} status={}",
                        masked(teamId),
                        provider.pathSegment(),
                        masked(keyId),
                        e.getStatusCode().value()
                );
                throw new ResponseStatusException(BAD_GATEWAY, "team credential lookup failed: " + e.getStatusCode(), e);
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                log.warn(
                        "team credential lookup connection failed teamId={} provider={} keyId={} cause={}",
                        masked(teamId),
                        provider.pathSegment(),
                        masked(keyId),
                        e.toString()
                );
                throw new ResponseStatusException(BAD_GATEWAY, "team credential lookup connection failed", e);
            }
        }
        if (last404 != null) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "해당 팀에 등록된 사용 가능한 API Key가 없습니다.",
                    last404
            );
        }
        throw new ResponseStatusException(BAD_GATEWAY, "team credential lookup failed");
    }

    private TeamKeyCredentialResponse loadCredentialByProviderSegment(
            String teamId,
            String keyId,
            String providerSegment,
            String token,
            String pathTemplate
    ) {
        return teamKeyServiceWebClient.get()
                .uri(uriBuilder -> buildCredentialUri(uriBuilder, pathTemplate, keyId, teamId, providerSegment))
                .headers(h -> {
                    if (token != null && !token.isBlank()) {
                        h.setBearerAuth(token);
                    }
                })
                .retrieve()
                .bodyToMono(TeamKeyCredentialResponse.class)
                .block(Duration.ofSeconds(10));
    }

    private static java.net.URI buildCredentialUri(
            UriBuilder uriBuilder,
            String pathTemplate,
            String keyId,
            String teamId,
            String providerSegment
    ) {
        return uriBuilder
                .path(pathTemplate)
                .queryParam("teamId", teamId)
                .queryParam("provider", providerSegment)
                .build(keyId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String masked(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "-";
        }
        int keep = Math.min(4, normalized.length());
        return normalized.substring(0, keep) + "***";
    }
}
