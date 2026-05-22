package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Positive (resolved key) and negative (404) cache for fingerprint reverse lookup.
 */
@Component
public class FingerprintLookupCache {

    private final Cache<String, ApiKeyClient.ResolvedApiKey> positiveCache;
    private final Cache<String, Boolean> negativeCache;

    public FingerprintLookupCache(ProxyProperties proxyProperties) {
        ProxyProperties.FingerprintLookup cfg = proxyProperties.getFingerprintLookup();
        Duration positiveTtl = Duration.parse(cfg.getPositiveTtl());
        Duration negativeTtl = Duration.parse(cfg.getNegativeTtl());
        this.positiveCache = Caffeine.newBuilder()
                .expireAfterWrite(positiveTtl)
                .maximumSize(10_000)
                .build();
        this.negativeCache = Caffeine.newBuilder()
                .expireAfterWrite(negativeTtl)
                .maximumSize(10_000)
                .build();
    }

    public Mono<ApiKeyClient.ResolvedApiKey> resolve(
            String cacheKey,
            Mono<ApiKeyClient.ResolvedApiKey> loader
    ) {
        ApiKeyClient.ResolvedApiKey cached = positiveCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached);
        }
        if (negativeCache.getIfPresent(cacheKey) != null) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않은 API key 입니다"));
        }
        return loader.flatMap(resolved -> {
            positiveCache.put(cacheKey, resolved);
            return Mono.just(resolved);
        }).onErrorResume(ResponseStatusException.class, ex -> {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                negativeCache.put(cacheKey, Boolean.TRUE);
            }
            return Mono.error(ex);
        });
    }

    static String cacheKey(String providerName, String fingerprint64) {
        return providerName + ":" + fingerprint64;
    }
}
