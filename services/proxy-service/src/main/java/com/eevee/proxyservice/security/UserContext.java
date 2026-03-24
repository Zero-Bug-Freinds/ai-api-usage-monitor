package com.eevee.proxyservice.security;

public record UserContext(
        String userId,
        String organizationId,
        String teamId,
        String correlationId
) {
}
