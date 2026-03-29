package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * After security, attaches {@code X-User-Id}, optional org/team, and {@code X-Gateway-Auth} for Proxy and Usage HTTP.
 * See {@code docs/contracts/gateway-proxy.md}.
 */
@Component
public class ProxyTrustHeadersGatewayFilter implements GlobalFilter, Ordered {

    private static final String HDR_USER = "X-User-Id";
    private static final String HDR_ORG = "X-Org-Id";
    private static final String HDR_TEAM = "X-Team-Id";
    private static final String HDR_GATEWAY_AUTH = "X-Gateway-Auth";
    private static final String HDR_CORRELATION = "X-Correlation-Id";

    private final GatewayProperties gatewayProperties;

    public ProxyTrustHeadersGatewayFilter(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!requiresGatewayTrustHeaders(path)) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> applyTrustHeaders(exchange, chain, ctx.getAuthentication()))
                .switchIfEmpty(Mono.defer(() -> {
                    if (gatewayProperties.isDevMode()) {
                        return forwardDevHeaders(exchange, chain);
                    }
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated"));
                }));
    }

    /**
     * 동일 패키지 단위 테스트용. 런타임은 {@link #filter} → SecurityContext 경로만 사용한다.
     */
    Mono<Void> applyTrustHeaders(ServerWebExchange exchange, GatewayFilterChain chain, Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return forwardWithJwt(exchange, chain, jwtAuth);
        }
        if (gatewayProperties.isDevMode()
                && (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken)) {
            return forwardDevHeaders(exchange, chain);
        }
        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated"));
    }

    private Mono<Void> forwardWithJwt(ServerWebExchange exchange, GatewayFilterChain chain, JwtAuthenticationToken jwtAuth) {
        Jwt jwt = jwtAuth.getToken();
        ServerHttpRequest.Builder req = exchange.getRequest().mutate();
        req.header(HDR_USER, jwt.getSubject());
        copyCorrelation(exchange, req);
        String org = jwt.getClaimAsString("org_id");
        if (org != null && !org.isBlank()) {
            req.header(HDR_ORG, org);
        }
        String team = jwt.getClaimAsString("team_id");
        if (team != null && !team.isBlank()) {
            req.header(HDR_TEAM, team);
        }
        attachGatewayAuth(req);
        return chain.filter(exchange.mutate().request(req.build()).build());
    }

    private Mono<Void> forwardDevHeaders(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst(HDR_USER);
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id"));
        }
        ServerHttpRequest.Builder req = exchange.getRequest().mutate();
        copyCorrelation(exchange, req);
        attachGatewayAuth(req);
        return chain.filter(exchange.mutate().request(req.build()).build());
    }

    private void copyCorrelation(ServerWebExchange exchange, ServerHttpRequest.Builder req) {
        String c = exchange.getRequest().getHeaders().getFirst(HDR_CORRELATION);
        if (c != null && !c.isBlank()) {
            req.header(HDR_CORRELATION, c);
        }
    }

    private void attachGatewayAuth(ServerHttpRequest.Builder req) {
        String secret = gatewayProperties.getSharedSecret();
        if (secret != null && !secret.isBlank()) {
            req.header(HDR_GATEWAY_AUTH, secret);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1000;
    }

    static boolean requiresGatewayTrustHeaders(String path) {
        return path.startsWith("/api/v1/ai/") || path.startsWith("/api/v1/usage/");
    }
}