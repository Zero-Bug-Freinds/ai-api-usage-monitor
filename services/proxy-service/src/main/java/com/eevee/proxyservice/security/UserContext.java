package com.eevee.proxyservice.security;

public record UserContext(
        String userId,
        String platformUserId,
        String organizationId,
        String teamId,
        String correlationId
) {
    public String keyLookupUserId() {
        return platformUserId != null && !platformUserId.isBlank() ? platformUserId : userId;
    }
}
