package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebEdgePreAuthWebFilterTest {

    @Test
    void trustedHeaders_bindAuthenticationContext() {
        GatewayProperties props = new GatewayProperties();
        props.setSharedSecret("local-secret");
        WebEdgePreAuthWebFilter filter = new WebEdgePreAuthWebFilter(props);

        MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/usage/dashboard/summary")
                .header("X-Web-Edge-Auth", "local-secret")
                .header("X-Auth-UserId", "42")
                .header("X-Auth-Subject", "user@example.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        AtomicReference<String> principalSeen = new AtomicReference<>();

        WebFilterChain chain = ex -> ReactiveSecurityContextHolder.getContext()
                .doOnNext(ctx -> principalSeen.set(String.valueOf(ctx.getAuthentication().getPrincipal())))
                .then();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(principalSeen.get()).isEqualTo("42");
    }

    @Test
    void missingTrustedHeaders_skipPreAuthentication() {
        GatewayProperties props = new GatewayProperties();
        props.setSharedSecret("local-secret");
        WebEdgePreAuthWebFilter filter = new WebEdgePreAuthWebFilter(props);

        MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/usage/dashboard/summary")
                .header("X-Auth-UserId", "42")
                .header("X-Auth-Subject", "user@example.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        AtomicReference<Boolean> hasContext = new AtomicReference<>(false);

        WebFilterChain chain = ex -> ReactiveSecurityContextHolder.getContext()
                .doOnNext(ctx -> hasContext.set(true))
                .switchIfEmpty(Mono.empty())
                .then();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(hasContext.get()).isFalse();
    }
}
