package com.eevee.proxygateway.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * When JWT is not configured, allows dev-style auth via X-User-Id so upstream keys can be resolved.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LocalUserHeadersWebFilter implements WebFilter {

    private static final String HDR_USER = "X-User-Id";

    private final com.eevee.proxygateway.config.ProxyProperties proxyProperties;

    public LocalUserHeadersWebFilter(com.eevee.proxygateway.config.ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String jwtSecret = proxyProperties.getSecurity().getJwtSecret();
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            return chain.filter(exchange);
        }
        if (!proxyProperties.getSecurity().isLocalDevHeaders()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/proxy/")) {
            return chain.filter(exchange);
        }
        String userId = exchange.getRequest().getHeaders().getFirst(HDR_USER);
        if (userId == null || userId.isBlank()) {
            return chain.filter(exchange);
        }
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }
}
