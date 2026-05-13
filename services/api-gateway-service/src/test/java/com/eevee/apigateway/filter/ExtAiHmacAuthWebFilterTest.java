package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExtAiHmacAuthWebFilterTest {

    private static final String SHARED_SECRET = "local-dev-gateway-shared-secret-do-not-use-in-prod";
    private static final String EXT_KEY_ID = "ext-key-01";
    private static final String EXT_HMAC_SECRET = "ext-hmac-secret-key-at-least-32-characters";

    private GatewayProperties gatewayProperties;
    private ExtAiHmacAuthWebFilter filter;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.setSharedSecret(SHARED_SECRET);
        gatewayProperties.getExtAi().setEnabled(true);
        gatewayProperties.getExtAi().setKeyId(EXT_KEY_ID);
        gatewayProperties.getExtAi().setHmacSecret(EXT_HMAC_SECRET);
        gatewayProperties.getExtAi().setTimestampSkewSeconds(300);
        gatewayProperties.getExtAi().setNonceTtlSeconds(300);
        filter = new ExtAiHmacAuthWebFilter(gatewayProperties, new ExtAiNonceReplayGuard(300));
    }

    @Test
    void extRequest_withValidHmac_setsInternalHeaders() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-1";
        String userId = "external-user-1";
        String path = "/api/v1/ai/ext/google/v1beta/models/gemini-2.5-flash:generateContent";
        String signature = sign("POST", path, "", timestamp, nonce, userId, "team-55");

        MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .header("X-Web-Edge-Auth", SHARED_SECRET)
                .header("X-Ext-Key-Id", EXT_KEY_ID)
                .header("X-Ext-Timestamp", timestamp)
                .header("X-Ext-Nonce", nonce)
                .header("X-Ext-Signature", signature)
                .header("X-Ext-User-Id", userId)
                .header("X-Team-Id", "team-55")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> seenUser = new AtomicReference<>();
        AtomicReference<String> seenTeam = new AtomicReference<>();
        AtomicReference<String> seenScope = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            seenUser.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            seenTeam.set(ex.getRequest().getHeaders().getFirst("X-Team-Id"));
            seenScope.set(ex.getRequest().getHeaders().getFirst("X-Scope-Type"));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(seenUser.get()).isEqualTo(userId);
        assertThat(seenTeam.get()).isEqualTo("team-55");
        assertThat(seenScope.get()).isEqualTo("TEAM");
    }

    @Test
    void extRequest_withBadSignature_returns401() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/ai/ext/openai/v1/chat/completions")
                .header("X-Web-Edge-Auth", SHARED_SECRET)
                .header("X-Ext-Key-Id", EXT_KEY_ID)
                .header("X-Ext-Timestamp", timestamp)
                .header("X-Ext-Nonce", "nonce-2")
                .header("X-Ext-Signature", "invalid-signature")
                .header("X-Ext-User-Id", "external-user-2")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
                .expectErrorMatches(err -> err instanceof org.springframework.web.server.ResponseStatusException
                        && ((org.springframework.web.server.ResponseStatusException) err).getStatusCode().equals(HttpStatus.UNAUTHORIZED))
                .verify();
    }

    @Test
    void extRequest_withReplayedNonce_returns409() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-replay";
        String path = "/api/v1/ai/ext/openai/v1/chat/completions";
        String signature = sign("POST", path, "", timestamp, nonce, "external-user-3", null);

        MockServerHttpRequest first = MockServerHttpRequest.post(path)
                .header("X-Web-Edge-Auth", SHARED_SECRET)
                .header("X-Ext-Key-Id", EXT_KEY_ID)
                .header("X-Ext-Timestamp", timestamp)
                .header("X-Ext-Nonce", nonce)
                .header("X-Ext-Signature", signature)
                .header("X-Ext-User-Id", "external-user-3")
                .build();
        MockServerHttpRequest second = MockServerHttpRequest.post(path)
                .header("X-Web-Edge-Auth", SHARED_SECRET)
                .header("X-Ext-Key-Id", EXT_KEY_ID)
                .header("X-Ext-Timestamp", timestamp)
                .header("X-Ext-Nonce", nonce)
                .header("X-Ext-Signature", signature)
                .header("X-Ext-User-Id", "external-user-3")
                .build();

        StepVerifier.create(filter.filter(MockServerWebExchange.from(first), ex -> Mono.empty()))
                .verifyComplete();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(second), ex -> Mono.empty()))
                .expectErrorMatches(err -> err instanceof org.springframework.web.server.ResponseStatusException
                        && ((org.springframework.web.server.ResponseStatusException) err).getStatusCode().equals(HttpStatus.CONFLICT))
                .verify();
    }

    @Test
    void extRequest_withoutExtUserId_keepsUserHeadersEmpty() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-no-user";
        String path = "/api/v1/ai/ext/openai/v1/chat/completions";
        String signature = sign("POST", path, "", timestamp, nonce, null, null);
        AtomicReference<String> seenUser = new AtomicReference<>("preset");

        MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .header("X-Web-Edge-Auth", SHARED_SECRET)
                .header("X-Ext-Key-Id", EXT_KEY_ID)
                .header("X-Ext-Timestamp", timestamp)
                .header("X-Ext-Nonce", nonce)
                .header("X-Ext-Signature", signature)
                .build();

        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), ex -> {
                    seenUser.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
                    return Mono.empty();
                }))
                .verifyComplete();
        assertThat(seenUser.get()).isNull();
    }

    private static String sign(
            String method,
            String path,
            String query,
            String timestamp,
            String nonce,
            String extUserId,
            String teamId
    ) {
        String canonical = String.join("\n",
                method,
                path,
                query,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                timestamp,
                nonce,
                EXT_KEY_ID,
                extUserId == null ? "" : extUserId,
                teamId == null ? "" : teamId
        );
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(EXT_HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
