package com.eevee.proxyservice.security;

public record UserContext(
        String userId,
        String platformUserId,
        String organizationId,
        String teamId,
        String correlationId
) {
    /**
     * Internal API key lookup uses numeric user PK when present; otherwise falls back to gateway subject (e.g. email).
     */
    public String keyLookupUserId() {
        return (platformUserId != null && !platformUserId.isBlank()) ? platformUserId : userId;
    }
}
