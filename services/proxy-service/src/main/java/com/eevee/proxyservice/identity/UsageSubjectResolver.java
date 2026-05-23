package com.eevee.proxyservice.identity;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.proxyservice.key.FingerprintOwnerLookup;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps fingerprint lookup owner ids to usage-event {@code userId} (JWT {@code sub} email when possible).
 */
@Component
public class UsageSubjectResolver {

    private final IdentityUsageSubjectClient identityUsageSubjectClient;
    private final boolean enabled;
    private final LoadingCache<String, Optional<String>> opaqueToEmailCache;

    public UsageSubjectResolver(IdentityUsageSubjectClient identityUsageSubjectClient, ProxyProperties proxyProperties) {
        this.identityUsageSubjectClient = identityUsageSubjectClient;
        ProxyProperties.UsageSubject usageSubject = proxyProperties.getUsageSubject();
        this.enabled = usageSubject.isEnabled();
        Duration cacheTtl = Duration.parse(usageSubject.getCacheTtl());
        this.opaqueToEmailCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(10_000)
                .build(key -> identityUsageSubjectClient.resolveEmailFromOpaqueOwner(key));
    }

    /**
     * Resolves the subject stored on {@link com.eevee.proxyservice.key.ApiKeyClient.ResolvedApiKey#usageSubjectUserId()}
     * and {@link com.eevee.usage.events.UsageRecordedEvent#userId()}.
     */
    public String resolveForFingerprintOwner(FingerprintOwnerLookup owner, String gatewaySubjectFallback) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is required");
        }
        if (!enabled) {
            if (owner.isTeam()) {
                return firstPresent(
                        UsageSubjectNormalizer.normalizeSubject(gatewaySubjectFallback),
                        UsageSubjectNormalizer.normalizeSubject(owner.userId())
                );
            }
            return firstPresent(
                    UsageSubjectNormalizer.normalizeSubject(owner.userId()),
                    UsageSubjectNormalizer.normalizeSubject(gatewaySubjectFallback)
            );
        }
        if (owner.isTeam()) {
            return firstPresent(
                    UsageSubjectNormalizer.normalizeSubject(gatewaySubjectFallback),
                    UsageSubjectNormalizer.normalizeSubject(owner.userId())
            );
        }
        return resolvePersonalFingerprintOwner(owner.userId(), gatewaySubjectFallback);
    }

    private String resolvePersonalFingerprintOwner(String rawOwnerUserId, String gatewaySubjectFallback) {
        if (rawOwnerUserId == null || rawOwnerUserId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "personal fingerprint lookup returned empty userId"
            );
        }
        if (UsageSubjectNormalizer.looksLikeEmail(rawOwnerUserId)) {
            return UsageSubjectNormalizer.normalizeEmail(rawOwnerUserId);
        }
        String cacheKey = rawOwnerUserId.trim().toLowerCase(Locale.ROOT);
        Optional<String> cached = opaqueToEmailCache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }
        Optional<String> direct = identityUsageSubjectClient.resolveEmailFromOpaqueOwner(rawOwnerUserId);
        if (direct.isPresent()) {
            opaqueToEmailCache.put(cacheKey, direct);
            return direct.get();
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "failed to resolve usage subject email for fingerprint owner"
        );
    }

    private static String firstPresent(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
