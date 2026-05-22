package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceAuthWebFilterTest {

    private static final String SHARED_SECRET = "local-dev-gateway-shared-secret-do-not-use-in-prod";
    private static final String INTERNAL_TOKEN = "gateway-internal-bearer-token-for-tests";

    private GatewayProperties gatewayProperties;
    private InternalServiceAuthWebFilter filter;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.setSharedSecret(SHARED_SECRET);
        gatewayProperties.getInternalAuth().setBearerToken(INTERNAL_TOKEN);
        filter = new InternalServiceAuthWebFilter(gatewayProperties);
    }

    @Test
    void internalPath_withWebEdgeAuth_passes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/web-edge/auth/resolve")
                        .header("X-Web-Edge-Auth", SHARED_SECRET)
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();
    }

    @Test
    void internalPath_withBearerToken_passes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/some-endpoint")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();
    }

    @Test
    void internalPath_withoutCredentials_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/some-endpoint").build()
        );

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) err).getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verify();
    }

    @Test
    void publicPath_isNoOp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/ai/openai/v1/chat/completions").build()
        );

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();
    }
}
