package com.eevee.apigateway.web;

import com.eevee.apigateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebEdgeAuthControllerTest {

    @Test
    void trustedWebEdgeRequest_returns200AndAuthHeaders() {
        GatewayProperties props = new GatewayProperties();
        props.setSharedSecret("edge-secret");
        ReactiveJwtDecoder decoder = mock(ReactiveJwtDecoder.class);
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of("sub", "user@example.com", "userId", "101")
        );
        when(decoder.decode("token")).thenReturn(Mono.just(jwt));

        WebEdgeAuthController controller = new WebEdgeAuthController(props, decoder);

        StepVerifier.create(controller.resolve("edge-secret", "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    HttpHeaders headers = response.getHeaders();
                    assertThat(headers.getFirst("X-Auth-Subject")).isEqualTo("user@example.com");
                    assertThat(headers.getFirst("X-Auth-UserId")).isEqualTo("101");
                })
                .verifyComplete();
    }
}
