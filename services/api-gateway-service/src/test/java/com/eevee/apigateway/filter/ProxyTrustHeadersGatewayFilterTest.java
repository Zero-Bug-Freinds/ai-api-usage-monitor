package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E 대체: 운영 JWT 모드에서 {@code sub} → {@code X-User-Id} 정합, 개발 모드에서 클라이언트 {@code X-User-Id} 전달.
 * 문서: {@code docs/contracts/gateway-proxy.md} §4.2.
 */
class ProxyTrustHeadersGatewayFilterTest {

    private GatewayProperties gatewayProperties;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.setDevMode(true);
        gatewayProperties.setSharedSecret("");
    }

    @Test
    void jwtSubjectIsForwardedAsXUserId_forUsagePath() {
        gatewayProperties.setDevMode(false);
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("user@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/usage/dashboard/summary").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> userIdSeen = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            userIdSeen.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            return Mono.empty();
        };

        ProxyTrustHeadersGatewayFilter filter = new ProxyTrustHeadersGatewayFilter(gatewayProperties);

        // contextWrite만 쓰면 일부 환경에서 getContext()가 empty → 401. defer + StepVerifier 초기 Context로 정합.
        StepVerifier.create(
                        Mono.defer(() -> filter.filter(exchange, chain)),
                        StepVerifierOptions.create()
                                .withInitialContext(ReactiveSecurityContextHolder.withAuthentication(auth))
                )
                .verifyComplete();

        assertThat(userIdSeen.get()).isEqualTo("user@example.com");
    }

    @Test
    void devMode_forwardsInboundXUserId_whenSecurityContextIsAnonymous() {
        AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken(
                "anon-key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        );

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/usage/dashboard/summary")
                .header("X-User-Id", "bff-session@local.dev")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> userIdSeen = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            userIdSeen.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            return Mono.empty();
        };

        ProxyTrustHeadersGatewayFilter filter = new ProxyTrustHeadersGatewayFilter(gatewayProperties);

        StepVerifier.create(
                        Mono.defer(() -> filter.filter(exchange, chain)),
                        StepVerifierOptions.create()
                                .withInitialContext(ReactiveSecurityContextHolder.withAuthentication(anon))
                )
                .verifyComplete();

        assertThat(userIdSeen.get()).isEqualTo("bff-session@local.dev");
    }

    @Test
    void pathsOutsideAiAndUsage_skipFilter() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<Boolean> chainRan = new AtomicReference<>(false);
        GatewayFilterChain chain = ex -> {
            chainRan.set(true);
            return Mono.empty();
        };

        ProxyTrustHeadersGatewayFilter filter = new ProxyTrustHeadersGatewayFilter(gatewayProperties);

        StepVerifier.create(
                        Mono.defer(() -> filter.filter(exchange, chain)),
                        StepVerifierOptions.create()
                                .withInitialContext(ReactiveSecurityContextHolder.withAuthentication(
                                        new AnonymousAuthenticationToken(
                                                "k", "a", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))))
                )
                .verifyComplete();

        assertThat(chainRan.get()).isTrue();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }
}
