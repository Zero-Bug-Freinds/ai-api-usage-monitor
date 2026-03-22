package com.eevee.proxygateway.security;

public record UserContext(
        String userId,
        String organizationId,
        String teamId,
        String correlationId
) {
}
