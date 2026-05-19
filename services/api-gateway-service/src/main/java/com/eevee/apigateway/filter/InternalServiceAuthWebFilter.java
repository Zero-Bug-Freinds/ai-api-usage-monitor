package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Gates gateway {@code /internal/**} endpoints: trusted edge secret or configured internal bearer token.
 */
public class InternalServiceAuthWebFilter implements WebFilter {

    private static final String HDR_WEB_EDGE_AUTH = "X-Web-Edge-Auth";
    private static final String HDR_AUTHORIZATION = "Authorization";

    private final GatewayProperties gatewayProperties;

    public InternalServiceAuthWebFilter(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }
        if (isAuthorized(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized internal request"));
    }

    private boolean isAuthorized(ServerHttpRequest request) {
        String sharedSecret = gatewayProperties.getSharedSecret();
        String edgeAuth = request.getHeaders().getFirst(HDR_WEB_EDGE_AUTH);
        if (StringUtils.hasText(sharedSecret)
                && StringUtils.hasText(edgeAuth)
                && constantTimeEquals(edgeAuth.trim(), sharedSecret)) {
            return true;
        }
        String internalToken = gatewayProperties.getInternalAuth().getBearerToken();
        if (!StringUtils.hasText(internalToken)) {
            return false;
        }
        String authorization = request.getHeaders().getFirst(HDR_AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.trim().regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = authorization.trim().substring(7).trim();
        return constantTimeEquals(token, internalToken.trim());
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
