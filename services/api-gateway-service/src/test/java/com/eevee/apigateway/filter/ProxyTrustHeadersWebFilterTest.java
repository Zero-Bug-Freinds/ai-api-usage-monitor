package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * E2E 대체: 운영 JWT 모드에서 {@code userId} claim → {@code X-User-Id}/{@code X-Platform-User-Id} 정합,
 * 개발 모드에서 클라이언트 {@code X-User-Id} 전달.
 * 문서: {@code docs/contracts/gateway-proxy.md} §4.2.
 */
class ProxyTrustHeadersWebFilterTest {

    private GatewayProperties gatewayProperties;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.setDevMode(true);
        gatewayProperties.setSharedSecret("");
    }

    @Test
    void authenticationFromExchangeOnly_emitsJwtWhenGetPrincipalReturnsJwtToken() {
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("principal-only@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getPrincipal()).thenReturn(Mono.just(auth));

        StepVerifier.create(ProxyTrustHeadersWebFilter.authenticationFromExchangeOnly(exchange))
                .expectNext(auth)
                .verifyComplete();
    }

    @Test
    void authenticationFromExchangeOnly_emptyWhenGetPrincipalEmpty() {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getPrincipal()).thenReturn(Mono.empty());

        StepVerifier.create(ProxyTrustHeadersWebFilter.authenticationFromExchangeOnly(exchange))
                .verifyComplete();
    }

    @Test
    void resolveAuthentication_cachesResultPerRequest() {
        AtomicInteger principalSubscriptions = new AtomicInteger();
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("cached@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        Map<String, Object> attrs = new HashMap<>();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getAttributes()).thenReturn(attrs);
        when(exchange.getPrincipal()).thenReturn(Mono.defer(() -> {
            principalSubscriptions.incrementAndGet();
            return Mono.just(auth);
        }));

        StepVerifier.create(ProxyTrustHeadersWebFilter.resolveAuthentication(exchange))
                .expectNext(auth)
                .verifyComplete();
        StepVerifier.create(ProxyTrustHeadersWebFilter.resolveAuthentication(exchange))
                .expectNext(auth)
                .verifyComplete();

        assertThat(principalSubscriptions.get()).isEqualTo(1);
    }

    @Test
    void resolveAuthentication_fallbacksToReactiveContextWhenPrincipalEmpty() {
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("context@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getAttributes()).thenReturn(new HashMap<>());
        when(exchange.getPrincipal()).thenReturn(Mono.empty());

        StepVerifier.create(
                        ProxyTrustHeadersWebFilter.resolveAuthentication(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .expectNext(auth)
                .verifyComplete();
    }

    @Test
    void resolveAuthentication_fallbacksToExchangeAttributeContext() {
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("attr@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextImpl context = new SecurityContextImpl(auth);
        HashMap<String, Object> attrs = new HashMap<>();
        attrs.put("org.springframework.security.SECURITY_CONTEXT", context);

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getAttributes()).thenReturn(attrs);
        when(exchange.getPrincipal()).thenReturn(Mono.empty());

        StepVerifier.create(ProxyTrustHeadersWebFilter.resolveAuthentication(exchange))
                .expectNext(auth)
                .verifyComplete();
    }

    @Test
    void applyTrustHeaders_rejectsNonJwtAuthInNonDevMode() {
        gatewayProperties.setDevMode(false);
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pwd", "ROLE_USER");
        auth.setAuthenticated(true);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/usage/dashboard/summary").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = ex -> Mono.empty();

        ProxyTrustHeadersWebFilter filter = new ProxyTrustHeadersWebFilter(gatewayProperties);

        StepVerifier.create(filter.applyTrustHeaders(exchange, chain, auth))
                .expectErrorMatches(t -> t instanceof org.springframework.web.server.ResponseStatusException
                        && ((org.springframework.web.server.ResponseStatusException) t).getStatusCode().value() == 401)
                .verify();
    }

    @Test
    void jwtUserIdClaimIsForwardedAsXUserId_forUsagePath() {
        gatewayProperties.setDevMode(false);
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("user@example.com")
                .claim("userId", "42")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/usage/dashboard/summary").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> userIdSeen = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            userIdSeen.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            return Mono.empty();
        };

        ProxyTrustHeadersWebFilter filter = new ProxyTrustHeadersWebFilter(gatewayProperties);

        StepVerifier.create(filter.applyTrustHeaders(exchange, chain, auth))
                .verifyComplete();

        assertThat(userIdSeen.get()).isEqualTo("42");
    }

    @Test
    void jwtUserIdClaimForwardedAsXPlatformUserId_forUsagePath() {
        gatewayProperties.setDevMode(false);
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("user@example.com")
                .claim("userId", "42")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/usage/dashboard/summary").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> platformUserIdSeen = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            platformUserIdSeen.set(ex.getRequest().getHeaders().getFirst("X-Platform-User-Id"));
            return Mono.empty();
        };

        ProxyTrustHeadersWebFilter filter = new ProxyTrustHeadersWebFilter(gatewayProperties);

        StepVerifier.create(filter.applyTrustHeaders(exchange, chain, auth))
                .verifyComplete();

        assertThat(platformUserIdSeen.get()).isEqualTo("42");
    }

    @Test
    void jwtWithoutUserIdClaim_isRejectedInNonDevMode() {
        gatewayProperties.setDevMode(false);
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("user@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/identity/auth/session").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = ex -> Mono.empty();

        ProxyTrustHeadersWebFilter filter = new ProxyTrustHeadersWebFilter(gatewayProperties);

        StepVerifier.create(filter.applyTrustHeaders(exchange, chain, auth))
                .expectErrorMatches(t -> t instanceof org.springframework.web.server.ResponseStatusException
                        && ((org.springframework.web.server.ResponseStatusException) t).getStatusCode().value() == 401)
                .verify();
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
        WebFilterChain chain = ex -> {
            userIdSeen.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            return Mono.empty();
        };

        ProxyTrustHeadersWebFilter filter = new ProxyTrustHeadersWebFilter(gatewayProperties);

        StepVerifier.create(filter.applyTrustHeaders(exchange, chain, anon))
                .verifyComplete();

        assertThat(userIdSeen.get()).isEqualTo("bff-session@local.dev");
    }

    @Test
    void devMode_forwardsInboundXPlatformUserId_whenPresent() {
        AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken(
                "anon-key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        );

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/usage/dashboard/summary")
                .header("X-User-Id", "bff-session@local.dev")
                .header("X-Platform-User-Id", "99")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> platformUserIdSeen = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            platformUserIdSeen.set(ex.getRequest().getHeaders().getFirst("X-Platform-User-Id"));
            return Mono.empty();
        };

        ProxyTrustHeadersWebFilter filter = new ProxyTrustHeadersWebFilter(gatewayProperties);

        StepVerifier.create(filter.applyTrustHeaders(exchange, chain, anon))
                .verifyComplete();

        assertThat(platformUserIdSeen.get()).isEqualTo("99");
    }

    @Test
    void pathsOutsideAiAndUsage_skipFilter() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<Boolean> chainRan = new AtomicReference<>(false);
        WebFilterChain chain = ex -> {
            chainRan.set(true);
            return Mono.empty();
        };

        ProxyTrustHeadersWebFilter filter = new ProxyTrustHeadersWebFilter(gatewayProperties);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainRan.get()).isTrue();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }
}
