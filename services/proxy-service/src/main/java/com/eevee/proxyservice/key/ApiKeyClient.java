package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.AiProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Resolves provider API keys from API Key Service (or mock). Keys are never logged.
 */
@Service
public class ApiKeyClient {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyClient.class);

    private final ProxyProperties proxyProperties;
    private final WebClient identityKeyServiceWebClient;
    private final WebClient teamKeyServiceWebClient;
    private final LoadingCache<String, ResolvedApiKey> cache;

    public ApiKeyClient(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
        this.identityKeyServiceWebClient = WebClient.builder()
                .baseUrl(proxyProperties.getKeyService().getBaseUrl())
                .build();
        this.teamKeyServiceWebClient = WebClient.builder()
                .baseUrl(proxyProperties.getTeamKeyService().getBaseUrl())
                .build();
        Duration ttl = Duration.parse(proxyProperties.getKeyService().getCacheTtl());
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .build(this::loadKeyBlocking);
    }

    /**
     * @param keyLookupUserId numeric platform user id when available, else gateway subject (e.g. email)
     */
    public Mono<ResolvedApiKey> resolveApiKey(String keyLookupUserId, String teamId, AiProvider provider) {
        String normalizedTeamId = teamId == null ? "" : teamId.trim();
        String cacheKey = keyLookupUserId + ":" + normalizedTeamId + ":" + provider.pathSegment();
        return Mono.fromCallable(() -> cache.get(cacheKey))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ResolvedApiKey loadKeyBlocking(String cacheKey) {
        int firstIdx = cacheKey.indexOf(':');
        int lastIdx = cacheKey.lastIndexOf(':');
        if (firstIdx <= 0 || lastIdx <= firstIdx) {
            throw new IllegalStateException("invalid cache key");
        }
        String keyLookupUserId = cacheKey.substring(0, firstIdx);
        String teamId = cacheKey.substring(firstIdx + 1, lastIdx);
        String segment = cacheKey.substring(lastIdx + 1);
        AiProvider provider = AiProvider.fromPathSegment(segment);
        boolean teamRequest = !teamId.isBlank();
        String userForLog = masked(keyLookupUserId);
        String teamForLog = teamRequest ? masked(teamId) : "-";
        String lookupTarget = teamRequest ? "team-service" : "identity-service";

        String mock = resolveMockKey(provider);
        if (mock != null && !mock.isBlank()) {
            log.info("Resolved API key from mock lookupTarget={} provider={} teamId={} user={}",
                    lookupTarget, provider.pathSegment(), teamForLog, userForLog);
            return new ResolvedApiKey(mock, null, null, fingerprint(mock), "mock");
        }

        try {
            KeyResponse body;
            if (teamRequest) {
                body = loadTeamKey(keyLookupUserId, teamId, provider);
            } else {
                body = loadIdentityKey(keyLookupUserId, provider);
            }
            if (body == null || body.plainKey() == null || body.plainKey().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Key lookup returned empty key");
            }
            log.info("Resolved API key lookupTarget={} provider={} teamId={} user={} keySource={}",
                    lookupTarget, provider.pathSegment(), teamForLog, userForLog, teamRequest ? "team" : "managed");
            return new ResolvedApiKey(
                    body.plainKey(),
                    body.keyId(),
                    teamRequest ? body.keyId() : null,
                    fingerprint(body.plainKey()),
                    teamRequest ? "team" : "managed"
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("API key lookup not found lookupTarget={} provider={} teamId={} user={} status={}",
                        lookupTarget, provider.pathSegment(), teamForLog, userForLog, e.getStatusCode().value());
                String message = teamRequest
                        ? "team API key not found for provider=" + provider.pathSegment() + " teamId=" + teamForLog
                        : "API key not found for provider=" + provider.pathSegment() + " user=" + userForLog;
                throw new ResponseStatusException(NOT_FOUND, message, e);
            }
            log.warn("API key lookup failed lookupTarget={} provider={} teamId={} user={} status={}",
                    lookupTarget, provider.pathSegment(), teamForLog, userForLog, e.getStatusCode().value());
            throw new ResponseStatusException(BAD_GATEWAY, "Key lookup failed: " + e.getStatusCode(), e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("API key lookup connection failed lookupTarget={} provider={} teamId={} user={} cause={}",
                    lookupTarget, provider.pathSegment(), teamForLog, userForLog, e.toString());
            throw new ResponseStatusException(BAD_GATEWAY, "Key lookup connection failed", e);
        }
    }

    private KeyResponse loadIdentityKey(String keyLookupUserId, AiProvider provider) {
        String token = proxyProperties.getKeyService().getInternalToken();
        String primaryProvider = provider.pathSegment();
        String userForLog = masked(keyLookupUserId);
        try {
            return loadIdentityKeyByProviderSegment(keyLookupUserId, primaryProvider, token);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() != 404 || provider != AiProvider.GOOGLE) {
                throw e;
            }
        }

        try {
            return loadIdentityKeyByProviderSegment(keyLookupUserId, "gemini", token);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        NOT_FOUND,
                        "API key not found for provider=" + primaryProvider + " user=" + userForLog,
                        e
                );
            }
            throw e;
        }

    }

    private KeyResponse loadIdentityKeyByProviderSegment(String keyLookupUserId, String providerSegment, String token) {
        return identityKeyServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/api-keys/{provider}")
                        .queryParam("userId", keyLookupUserId)
                        .build(providerSegment))
                .headers(h -> {
                    if (token != null && !token.isBlank()) {
                        h.setBearerAuth(token);
                    }
                })
                .retrieve()
                .bodyToMono(KeyResponse.class)
                .block(Duration.ofSeconds(10));
    }

    private KeyResponse loadTeamKey(String keyLookupUserId, String teamId, AiProvider provider) {
        ProxyProperties.TeamKeyService teamKeyService = proxyProperties.getTeamKeyService();
        String token = teamKeyService.getInternalToken();
        String pathTemplate = teamKeyService.getPathTemplate();
        String primaryProvider = provider.pathSegment();
        String teamForLog = masked(teamId);
        try {
            return loadTeamKeyByProviderSegment(keyLookupUserId, teamId, primaryProvider, token, pathTemplate);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() != 404 || provider != AiProvider.GOOGLE) {
                throw e;
            }
        }

        try {
            return loadTeamKeyByProviderSegment(keyLookupUserId, teamId, "gemini", token, pathTemplate);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        NOT_FOUND,
                        "team API key not found for provider=" + primaryProvider + " teamId=" + teamForLog,
                        e
                );
            }
            throw e;
        }
    }

    private KeyResponse loadTeamKeyByProviderSegment(
            String keyLookupUserId,
            String teamId,
            String providerSegment,
            String token,
            String pathTemplate
    ) {
        return teamKeyServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(pathTemplate)
                        .queryParam("teamId", teamId)
                        .queryParam("userId", keyLookupUserId)
                        .build(providerSegment))
                .headers(h -> {
                    if (token != null && !token.isBlank()) {
                        h.setBearerAuth(token);
                    }
                })
                .retrieve()
                .bodyToMono(KeyResponse.class)
                .block(Duration.ofSeconds(10));
    }

    /**
     * Uses provider-specific mock keys when set; otherwise falls back to legacy {@code mock-key}.
     */
    private String resolveMockKey(AiProvider provider) {
        ProxyProperties.KeyService ks = proxyProperties.getKeyService();
        String specific = switch (provider) {
            case OPENAI -> ks.getMockKeyOpenai();
            case GOOGLE -> ks.getMockKeyGoogle();
            case ANTHROPIC -> null;
        };
        if (specific != null && !specific.isBlank()) {
            return specific;
        }
        String legacy = ks.getMockKey();
        return (legacy != null && !legacy.isBlank()) ? legacy : null;
    }

    static List<String> providerSegmentsForLookup(AiProvider provider) {
        return List.of(provider.pathSegment());
    }

    private static String masked(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        int keep = Math.min(4, value.length());
        return value.substring(0, keep) + "***";
    }

    private static String fingerprint(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record ResolvedApiKey(
            String plainKey,
            String keyId,
            String teamApiKeyId,
            String keyFingerprint,
            String keySource
    ) {
    }

    private record KeyResponse(String plainKey, String keyId) {
    }
}
