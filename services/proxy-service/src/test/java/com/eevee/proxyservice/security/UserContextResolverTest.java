package com.eevee.proxyservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextResolverTest {

    private final UserContextResolver resolver = new UserContextResolver();

    @Test
    void keyLookupUserId_prefersPlatformUserIdFromHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/google/")
                .header("X-User-Id", "a@b.com")
                .header("X-Platform-User-Id", "42")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(resolver.fromExchange(exchange))
                .assertNext(ctx -> {
                    assertThat(ctx.userId()).isEqualTo("a@b.com");
                    assertThat(ctx.platformUserId()).isEqualTo("42");
                    assertThat(ctx.keyLookupUserId()).isEqualTo("42");
                })
                .verifyComplete();
    }

    @Test
    void keyLookupUserId_fallsBackToUserIdWhenPlatformAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/google/")
                .header("X-User-Id", "a@b.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(resolver.fromExchange(exchange))
                .assertNext(ctx -> {
                    assertThat(ctx.platformUserId()).isNull();
                    assertThat(ctx.keyLookupUserId()).isEqualTo("a@b.com");
                })
                .verifyComplete();
    }
}
