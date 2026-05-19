package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import com.eevee.apigateway.util.ApiKeyFingerprintHasher;
import com.eevee.apigateway.util.ExtProviderApiKeyExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExtAiApiKeyFingerprintWebFilterTest {

    private GatewayProperties gatewayProperties;
    private ExtAiApiKeyFingerprintWebFilter filter;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.getExtAi().setEnabled(true);
        filter = new ExtAiApiKeyFingerprintWebFilter(gatewayProperties);
    }

    @Test
    void extPath_withApiKey_setsFingerprintAndStripsRawHeaders() {
        String plainKey = "sk-test-openai-key";
        String path = "/api/v1/ai/ext/openai/v1/chat/completions";
        MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .header(ExtProviderApiKeyExtractor.HDR_X_API_KEY, plainKey)
                .header("X-Ext-Key-Id", "kid")
                .header("X-Ext-Signature", "sig")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ServerHttpRequest out = forwarded.get();
        assertThat(out.getHeaders().getFirst("X-Api-Key-Fingerprint"))
                .isEqualTo(ApiKeyFingerprintHasher.sha256HexUtf8(plainKey));
        assertThat(out.getHeaders().getFirst("X-Ai-Provider")).isEqualTo("OPENAI");
        assertThat(out.getHeaders().getFirst(ExtProviderApiKeyExtractor.HDR_X_API_KEY)).isNull();
        assertThat(out.getHeaders().getFirst("X-Ext-Signature")).isNull();
    }

    @Test
    void extPath_withoutKey_forwardsUnchanged() {
        String path = "/api/v1/ai/ext/openai/v1/chat/completions";
        MockServerHttpRequest request = MockServerHttpRequest.post(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get().getHeaders().getFirst("X-Api-Key-Fingerprint")).isNull();
    }

    @Test
    void extPath_unknownProvider_returns400() {
        String path = "/api/v1/ai/ext/unknown/v1/chat/completions";
        MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .header(ExtProviderApiKeyExtractor.HDR_X_API_KEY, "sk-test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) err).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();
    }

    @Test
    void nonExtPath_isNoOp() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/ai/openai/v1/chat/completions")
                .header(ExtProviderApiKeyExtractor.HDR_X_API_KEY, "sk-test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get().getHeaders().getFirst(ExtProviderApiKeyExtractor.HDR_X_API_KEY))
                .isEqualTo("sk-test");
    }
}
