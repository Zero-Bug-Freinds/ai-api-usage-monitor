package com.eevee.proxyservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextResolverTest {

    private final UserContextResolver resolver = new UserContextResolver();

    @Test
    void fromExchange_readsUserAndPlatformUserHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/openai/v1/chat/completions")
                .header("X-User-Id", "user@example.com")
                .header("X-Platform-User-Id", "101")
                .header("X-Org-Id", "org-1")
                .header("X-Team-Id", "team-1")
                .header("X-Correlation-Id", "corr-1")
                .build();

        StepVerifier.create(resolver.fromExchange(MockServerWebExchange.from(request)))
                .assertNext(ctx -> {
                    assertThat(ctx.userId()).isEqualTo("user@example.com");
                    assertThat(ctx.platformUserId()).isEqualTo("101");
                    assertThat(ctx.keyLookupUserId()).isEqualTo("101");
                    assertThat(ctx.organizationId()).isEqualTo("org-1");
                    assertThat(ctx.teamId()).isEqualTo("team-1");
                    assertThat(ctx.correlationId()).isEqualTo("corr-1");
                })
                .verifyComplete();
    }

    @Test
    void keyLookupUserId_fallsBackToUserIdWhenPlatformHeaderMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/openai/v1/chat/completions")
                .header("X-User-Id", "user@example.com")
                .build();

        StepVerifier.create(resolver.fromExchange(MockServerWebExchange.from(request)))
                .assertNext(ctx -> {
                    assertThat(ctx.platformUserId()).isNull();
                    assertThat(ctx.keyLookupUserId()).isEqualTo("user@example.com");
                })
                .verifyComplete();
    }
}
