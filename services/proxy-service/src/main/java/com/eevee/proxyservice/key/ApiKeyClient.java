package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.AiProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
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

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Resolves provider API keys from API Key Service (or mock). Keys are never logged.
 */
@Service
public class ApiKeyClient {

    private final ProxyProperties proxyProperties;
    private final WebClient keyServiceWebClient;
    private final LoadingCache<String, ResolvedApiKey> cache;

    public ApiKeyClient(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
        this.keyServiceWebClient = WebClient.builder()
                .baseUrl(proxyProperties.getKeyService().getBaseUrl())
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
    public Mono<ResolvedApiKey> resolveApiKey(String keyLookupUserId, AiProvider provider) {
        String cacheKey = keyLookupUserId + ":" + provider.pathSegment();
        return Mono.fromCallable(() -> cache.get(cacheKey))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ResolvedApiKey loadKeyBlocking(String cacheKey) {
        int idx = cacheKey.indexOf(':');
        if (idx <= 0) {
            throw new IllegalStateException("invalid cache key");
        }
        String keyLookupUserId = cacheKey.substring(0, idx);
        String segment = cacheKey.substring(idx + 1);
        AiProvider provider = AiProvider.fromPathSegment(segment);

        String mock = resolveMockKey(provider);
        if (mock != null && !mock.isBlank()) {
            return new ResolvedApiKey(mock, null, fingerprint(mock), "mock");
        }

        String token = proxyProperties.getKeyService().getInternalToken();
        try {
            KeyResponse body = keyServiceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/api-keys/{provider}")
                            .queryParam("userId", keyLookupUserId)
                            .build(provider.pathSegment()))
                    .headers(h -> {
                        if (token != null && !token.isBlank()) {
                            h.setBearerAuth(token);
                        }
                    })
                    .retrieve()
                    .bodyToMono(KeyResponse.class)
                    .block(Duration.ofSeconds(10));
            if (body == null || body.plainKey() == null || body.plainKey().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Identity key lookup returned empty key");
            }
            return new ResolvedApiKey(
                    body.plainKey(),
                    body.keyId(),
                    fingerprint(body.plainKey()),
                    "managed"
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResponseStatusException(BAD_REQUEST, "No registered provider API key", e);
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Identity key lookup failed: " + e.getStatusCode(), e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Identity key lookup connection failed", e);
        }
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
            String keyFingerprint,
            String keySource
    ) {
    }

    private record KeyResponse(String plainKey, String keyId) {
    }
}
