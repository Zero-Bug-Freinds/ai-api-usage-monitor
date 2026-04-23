package com.eevee.proxyservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class UserContextResolver {

    private static final String HDR_USER = "X-User-Id";
    private static final String HDR_PLATFORM_USER = "X-Platform-User-Id";
    private static final String HDR_ORG = "X-Org-Id";
    private static final String HDR_TEAM = "X-Team-Id";
    private static final String HDR_CORRELATION = "X-Correlation-Id";

    public Mono<UserContext> fromExchange(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HDR_CORRELATION);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> mapAuthentication(ctx.getAuthentication(), exchange, finalCorrelationId))
                .switchIfEmpty(Mono.defer(() -> mapAuthentication(null, exchange, finalCorrelationId)));
    }

    private Mono<UserContext> mapAuthentication(
            Authentication auth,
            ServerWebExchange exchange,
            String correlationId
    ) {
        String platformUserId = firstNonBlankHeader(exchange, HDR_PLATFORM_USER);
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String userId) {
            String org = exchange.getRequest().getHeaders().getFirst(HDR_ORG);
            String team = exchange.getRequest().getHeaders().getFirst(HDR_TEAM);
            return Mono.just(new UserContext(userId, platformUserId, org, team, correlationId));
        }
        String userId = exchange.getRequest().getHeaders().getFirst(HDR_USER);
        if (userId != null && !userId.isBlank()) {
            String org = exchange.getRequest().getHeaders().getFirst(HDR_ORG);
            String team = exchange.getRequest().getHeaders().getFirst(HDR_TEAM);
            return Mono.just(new UserContext(userId, platformUserId, org, team, correlationId));
        }
        return Mono.error(new IllegalStateException("Missing X-User-Id (from Gateway)"));
    }

    private static String firstNonBlankHeader(ServerWebExchange exchange, String name) {
        String v = exchange.getRequest().getHeaders().getFirst(name);
        return (v != null && !v.isBlank()) ? v : null;
    }
}
