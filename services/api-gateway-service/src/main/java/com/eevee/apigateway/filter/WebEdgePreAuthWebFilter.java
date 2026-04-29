package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

public class WebEdgePreAuthWebFilter implements WebFilter {

    private static final String HDR_WEB_EDGE_AUTH = "X-Web-Edge-Auth";
    private static final String HDR_AUTH_USER_ID = "X-Auth-UserId";
    private static final String HDR_AUTH_SUBJECT = "X-Auth-Subject";
    private static final Logger log = LoggerFactory.getLogger(WebEdgePreAuthWebFilter.class);

    private final GatewayProperties gatewayProperties;

    public WebEdgePreAuthWebFilter(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String expectedSecret = gatewayProperties.getSharedSecret();
        String trustedHeader = exchange.getRequest().getHeaders().getFirst(HDR_WEB_EDGE_AUTH);
        String authUserId = exchange.getRequest().getHeaders().getFirst(HDR_AUTH_USER_ID);
        String authSubject = exchange.getRequest().getHeaders().getFirst(HDR_AUTH_SUBJECT);

        if (expectedSecret == null || expectedSecret.isBlank()
                || trustedHeader == null || !expectedSecret.equals(trustedHeader)
                || authUserId == null || authUserId.isBlank()
                || authSubject == null || authSubject.isBlank()) {
            return chain.filter(exchange);
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                authUserId,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        log.info("Pre-authenticated request from web-edge path={} userId={} subjectPrefix={}",
                path, mask(authUserId), mask(authSubject));
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        int keep = Math.min(4, value.length());
        return value.substring(0, keep) + "***";
    }
}
