package com.eevee.proxyservice.security;

import com.eevee.proxyservice.identity.UsageSubjectNormalizer;

public record UserContext(
        String userId,
        String platformUserId,
        String organizationId,
        String teamId,
        String correlationId,
        String requestedApiKeyId,
        String requestedApiKeyAlias,
        String extUserId,
        String rawApiKey,
        String apiKeyFingerprint64
) {
    /**
     * Internal API key lookup uses numeric user PK when present; otherwise falls back to gateway subject (e.g. email).
     */
    public String keyLookupUserId() {
        return (platformUserId != null && !platformUserId.isBlank()) ? platformUserId : userId;
    }

    /**
     * Canonical subject for {@link com.eevee.usage.events.UsageRecordedEvent#userId()} (JWT {@code sub} / gateway
     * {@code X-User-Id}). Must not use {@link #keyLookupUserId()} numeric PK. Stable across {@code enrichContext}.
     */
    public String usageEventUserId() {
        if (userId != null && !userId.isBlank()) {
            if (UsageSubjectNormalizer.looksLikeEmail(userId)) {
                return UsageSubjectNormalizer.normalizeEmail(userId);
            }
            return userId.trim();
        }
        if (extUserId != null && !extUserId.isBlank()) {
            return UsageSubjectNormalizer.normalizeSubject(extUserId);
        }
        return null;
    }

    public boolean hasUserContext() {
        return keyLookupUserId() != null && !keyLookupUserId().isBlank();
    }

    public boolean hasFingerprintMaterial() {
        return apiKeyFingerprint64 != null && !apiKeyFingerprint64.isBlank();
    }
}
