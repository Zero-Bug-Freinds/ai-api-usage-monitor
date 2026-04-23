package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final String AUTH_CACHE_ATTR = ProxyTrustHeadersWebFilter.class.getName() + ".authMono";
    private static final String SECURITY_CONTEXT_ATTR = "org.springframework.security.SECURITY_CONTEXT";
    private static final String HDR_USER = "X-User-Id";
    private static final String HDR_PLATFORM_USER = "X-Platform-User-Id";
    private static final String HDR_ORG = "X-Org-Id";
    private static final String HDR_TEAM = "X-Team-Id";
    private static final String HDR_GATEWAY_AUTH = "X-Gateway-Auth";
    private static final String HDR_CORRELATION = "X-Correlation-Id";
    private static final Logger log = LoggerFactory.getLogger(ProxyTrustHeadersWebFilter.class);

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
        log.info("ProxyTrustHeadersWebFilter auth resolution start path={} devMode={}",
                path, gatewayProperties.isDevMode());
        Mono<Authentication> authMono = resolveAuthentication(exchange);
        return authMono
                .flatMap(auth -> applyTrustHeaders(exchange, chain, auth))
                .switchIfEmpty(Mono.defer(() -> {
                    if (gatewayProperties.isDevMode()) {
                        return forwardDevHeaders(exchange, chain);
                    }
                    if (exchange.getResponse().isCommitted()) {
                        log.warn("Skip unauthenticated error because response already committed path={}", path);
                        return Mono.empty();
                    }
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated"));
                }));
    }

    /** Resolves {@link Authentication} from {@link ServerWebExchange#getPrincipal()} when present. */
    static Mono<Authentication> authenticationFromExchangeOnly(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .doOnNext(p -> log.info("Principal detected class={}", p.getClass().getName()))
                .switchIfEmpty(Mono.fromRunnable(() -> log.info("Principal missing from exchange")))
                .filter(Authentication.class::isInstance)
                .cast(Authentication.class);
    }

    /**
     * Request-scoped authentication resolution.
     * <p>
     * Store a cached Mono in exchange attributes so repeated subscriptions in
     * the same request path reuse the same authentication outcome.
     */
    @SuppressWarnings("unchecked")
    static Mono<Authentication> resolveAuthentication(ServerWebExchange exchange) {
        Object cached = exchange.getAttributes().get(AUTH_CACHE_ATTR);
        if (cached instanceof Mono<?> mono) {
            return (Mono<Authentication>) mono;
        }
        Mono<Authentication> resolved = authenticationFromExchangeOnly(exchange)
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext()
                        .doOnNext(ctx -> {
                            Authentication auth = ctx.getAuthentication();
                            log.info("ReactiveSecurityContext auth={}",
                                    auth == null ? "null" : auth.getClass().getName());
                        })
                        .switchIfEmpty(Mono.fromRunnable(
                                () -> log.info("ReactiveSecurityContext missing for request")))
                        .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication())))
                .switchIfEmpty(authenticationFromExchangeAttribute(exchange))
                .cache();
        exchange.getAttributes().put(AUTH_CACHE_ATTR, resolved);
        return resolved;
    }

    static Mono<Authentication> authenticationFromExchangeAttribute(ServerWebExchange exchange) {
        Object value = exchange.getAttributes().get(SECURITY_CONTEXT_ATTR);
        if (value instanceof SecurityContext context && context.getAuthentication() != null) {
            Authentication auth = context.getAuthentication();
            log.info("Exchange attribute security context auth={}", auth.getClass().getName());
            return Mono.just(auth);
        }
        log.info("Exchange attribute security context missing");
        return Mono.empty();
    }

    /**
     * 동일 패키지 단위 테스트용.
     */
    Mono<Void> applyTrustHeaders(ServerWebExchange exchange, WebFilterChain chain, Authentication auth) {
        log.info("applyTrustHeaders authType={}", auth == null ? "null" : auth.getClass().getName());
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return forwardWithJwt(exchange, chain, jwtAuth);
        }
        if (gatewayProperties.isDevMode()
                && (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken)) {
            return forwardDevHeaders(exchange, chain);
        }
        if (auth != null) {
            log.warn("Reject non-JWT authentication type={} path={}",
                    auth.getClass().getName(), exchange.getRequest().getPath().value());
        }
        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated"));
    }

    private Mono<Void> forwardWithJwt(ServerWebExchange exchange, WebFilterChain chain, JwtAuthenticationToken jwtAuth) {
        Jwt jwt = jwtAuth.getToken();
        ServerHttpRequest.Builder req = exchange.getRequest().mutate();
        sanitizeTrustHeaders(req);
        req.header(HDR_USER, jwt.getSubject());
        String platformUserId = jwt.getClaimAsString("userId");
        if (platformUserId != null && !platformUserId.isBlank()) {
            req.header(HDR_PLATFORM_USER, platformUserId);
        }
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
        sanitizeTrustHeaders(req);
        req.header(HDR_USER, userId);
        copyCorrelation(exchange, req);
        String platformUserId = exchange.getRequest().getHeaders().getFirst(HDR_PLATFORM_USER);
        if (platformUserId != null && !platformUserId.isBlank()) {
            req.header(HDR_PLATFORM_USER, platformUserId);
        }
        String org = exchange.getRequest().getHeaders().getFirst(HDR_ORG);
        if (org != null && !org.isBlank()) {
            req.header(HDR_ORG, org);
        }
        String team = exchange.getRequest().getHeaders().getFirst(HDR_TEAM);
        if (team != null && !team.isBlank()) {
            req.header(HDR_TEAM, team);
        }
        attachGatewayAuth(req);
        return chain.filter(exchange.mutate().request(req.build()).build());
    }

    private void sanitizeTrustHeaders(ServerHttpRequest.Builder req) {
        req.headers(headers -> {
            headers.remove(HDR_USER);
            headers.remove(HDR_PLATFORM_USER);
            headers.remove(HDR_ORG);
            headers.remove(HDR_TEAM);
            headers.remove(HDR_GATEWAY_AUTH);
        });
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
        return path.startsWith("/api/v1/ai/")
                || path.startsWith("/api/v1/usage/")
                || path.startsWith("/api/v1/expenditure/");
    }
}
