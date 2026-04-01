package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Runs inside the Security {@link org.springframework.security.web.server.SecurityWebFilterChain}
 * (after authorization). Resolves the authenticated user from {@link ServerWebExchange#getPrincipal()}
 * first, then {@link ReactiveSecurityContextHolder}, so trust headers still apply when the Reactor
 * security context is not populated on this subscriber chain. Attaches {@code X-User-Id}, optional
 * org/team, and {@code X-Gateway-Auth} for Proxy and Usage HTTP.
 * See {@code docs/contracts/gateway-proxy.md}.
 * <p>
 * Do not register this class as a Spring {@code @Component} or {@code @Bean} of type {@link WebFilter}:
 * Boot would add it to the global WebFilter chain (runs before security, empty
 * {@link ReactiveSecurityContextHolder}) while {@link SecurityConfiguration} also adds it after
 * authorization. Register only via {@code ServerHttpSecurity#addFilterAfter}.
 */
public class ProxyTrustHeadersWebFilter implements WebFilter {

    private static final String HDR_USER = "X-User-Id";
    private static final String HDR_ORG = "X-Org-Id";
    private static final String HDR_TEAM = "X-Team-Id";
    private static final String HDR_GATEWAY_AUTH = "X-Gateway-Auth";
    private static final String HDR_CORRELATION = "X-Correlation-Id";

    private final GatewayProperties gatewayProperties;

    public ProxyTrustHeadersWebFilter(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!requiresGatewayTrustHeaders(path)) {
            return chain.filter(exchange);
        }
        Mono<Authentication> authMono = authenticationFromExchangeOnly(exchange)
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext()
                        .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication())));
        return authMono
                .flatMap(auth -> applyTrustHeaders(exchange, chain, auth))
                .switchIfEmpty(Mono.defer(() -> {
                    if (gatewayProperties.isDevMode()) {
                        return forwardDevHeaders(exchange, chain);
                    }
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated"));
                }));
    }

    /** Resolves {@link Authentication} from {@link ServerWebExchange#getPrincipal()} when present. */
    static Mono<Authentication> authenticationFromExchangeOnly(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .filter(Authentication.class::isInstance)
                .cast(Authentication.class);
    }

    /**
     * 동일 패키지 단위 테스트용.
     */
    Mono<Void> applyTrustHeaders(ServerWebExchange exchange, WebFilterChain chain, Authentication auth) {
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

    private Mono<Void> forwardWithJwt(ServerWebExchange exchange, WebFilterChain chain, JwtAuthenticationToken jwtAuth) {
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

    private Mono<Void> forwardDevHeaders(ServerWebExchange exchange, WebFilterChain chain) {
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

    static boolean requiresGatewayTrustHeaders(String path) {
        return path.startsWith("/api/v1/ai/") || path.startsWith("/api/v1/usage/");
    }
}
