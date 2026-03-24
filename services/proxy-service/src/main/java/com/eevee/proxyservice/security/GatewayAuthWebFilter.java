package com.eevee.proxyservice.security;

import com.eevee.proxyservice.config.ProxyProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Ensures requests to {@code /proxy/**} come from the API Gateway when {@code proxy.gateway.require-auth} is true.
 * See {@code docs/contracts/gateway-proxy.md}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayAuthWebFilter implements WebFilter {

    public static final String HDR_GATEWAY_AUTH = "X-Gateway-Auth";

    private final ProxyProperties proxyProperties;

    public GatewayAuthWebFilter(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/proxy/")) {
            return chain.filter(exchange);
        }
        if (!proxyProperties.getGateway().isRequireAuth()) {
            return chain.filter(exchange);
        }
        String expected = proxyProperties.getGateway().getSharedSecret();
        if (expected == null || expected.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return exchange.getResponse().setComplete();
        }
        String incoming = exchange.getRequest().getHeaders().getFirst(HDR_GATEWAY_AUTH);
        if (!constantTimeEquals(expected, incoming)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private static boolean constantTimeEquals(String expected, String incoming) {
        if (incoming == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = incoming.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }
}
