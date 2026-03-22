package com.eevee.proxygateway.key;

import com.eevee.proxygateway.config.ProxyProperties;
import com.eevee.usage.events.AiProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Resolves provider API keys from API Key Service (or mock). Keys are never logged.
 */
@Service
public class ApiKeyClient {

    private final ProxyProperties proxyProperties;
    private final WebClient keyServiceWebClient;
    private final LoadingCache<String, String> cache;

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

    public Mono<String> resolveApiKey(String userId, AiProvider provider) {
        String cacheKey = userId + ":" + provider.pathSegment();
        return Mono.fromCallable(() -> cache.get(cacheKey))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String loadKeyBlocking(String cacheKey) {
        int idx = cacheKey.indexOf(':');
        if (idx <= 0) {
            throw new IllegalStateException("invalid cache key");
        }
        String userId = cacheKey.substring(0, idx);
        String segment = cacheKey.substring(idx + 1);
        AiProvider provider = AiProvider.fromPathSegment(segment);

        String mock = proxyProperties.getKeyService().getMockKey();
        if (mock != null && !mock.isBlank()) {
            return mock;
        }

        String token = proxyProperties.getKeyService().getInternalToken();
        try {
            KeyResponse body = keyServiceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/api-keys/{provider}")
                            .queryParam("userId", userId)
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
                throw new IllegalStateException("key service returned empty key");
            }
            return body.plainKey();
        } catch (WebClientResponseException e) {
            throw new IllegalStateException("key service error: " + e.getStatusCode(), e);
        }
    }

    private record KeyResponse(String plainKey) {
    }
}
