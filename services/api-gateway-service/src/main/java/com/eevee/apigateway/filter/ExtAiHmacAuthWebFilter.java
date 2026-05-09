package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

public class ExtAiHmacAuthWebFilter implements WebFilter {

    private static final String EXT_PREFIX = "/api/v1/ai/ext/";
    private static final String HDR_WEB_EDGE_AUTH = "X-Web-Edge-Auth";
    private static final String HDR_EXT_KEY_ID = "X-Ext-Key-Id";
    private static final String HDR_EXT_TIMESTAMP = "X-Ext-Timestamp";
    private static final String HDR_EXT_NONCE = "X-Ext-Nonce";
    private static final String HDR_EXT_BODY_SHA256 = "X-Ext-Body-Sha256";
    private static final String HDR_EXT_SIGNATURE = "X-Ext-Signature";
    private static final String HDR_EXT_USER_ID = "X-Ext-User-Id";
    private static final String HDR_TEAM_ID = "X-Team-Id";
    private static final String HDR_SCOPE_TYPE = "X-Scope-Type";
    private static final String HDR_GATEWAY_AUTH = "X-Gateway-Auth";
    private static final String HDR_USER = "X-User-Id";
    private static final String HDR_PLATFORM_USER = "X-Platform-User-Id";
    private static final String HDR_CORRELATION = "X-Correlation-Id";
    private static final String SHA256_EMPTY_HEX = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final GatewayProperties gatewayProperties;
    private final ExtAiNonceReplayGuard nonceReplayGuard;

    public ExtAiHmacAuthWebFilter(GatewayProperties gatewayProperties, ExtAiNonceReplayGuard nonceReplayGuard) {
        this.gatewayProperties = gatewayProperties;
        this.nonceReplayGuard = nonceReplayGuard;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(EXT_PREFIX)) {
            return chain.filter(exchange);
        }
        if (!gatewayProperties.getExtAi().isEnabled()) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "ext ai ingress is disabled"));
        }

        ServerHttpRequest request = exchange.getRequest();
        String edgeAuth = request.getHeaders().getFirst(HDR_WEB_EDGE_AUTH);
        if (!hasText(edgeAuth) || !edgeAuth.equals(gatewayProperties.getSharedSecret())) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "untrusted edge"));
        }

        String keyId = requiredHeader(request, HDR_EXT_KEY_ID);
        String timestampRaw = requiredHeader(request, HDR_EXT_TIMESTAMP);
        String nonce = requiredHeader(request, HDR_EXT_NONCE);
        String bodySha256 = normalizeBodyHash(request.getHeaders().getFirst(HDR_EXT_BODY_SHA256));
        String signature = requiredHeader(request, HDR_EXT_SIGNATURE);
        String extUserId = normalizeOptional(request.getHeaders().getFirst(HDR_EXT_USER_ID));
        String teamId = normalizeOptional(request.getHeaders().getFirst(HDR_TEAM_ID));

        if (!keyId.equals(gatewayProperties.getExtAi().getKeyId())) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid ext key id"));
        }

        Instant now = Instant.now();
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestampRaw);
        } catch (NumberFormatException ex) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid ext timestamp"));
        }
        long skew = Math.abs(now.getEpochSecond() - timestampSeconds);
        if (skew > gatewayProperties.getExtAi().getTimestampSkewSeconds()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "expired ext timestamp"));
        }

        if (!nonceReplayGuard.tryAcquire(keyId + ":" + nonce, now)) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "replayed ext nonce"));
        }

        String canonical = canonicalString(request, bodySha256, timestampRaw, nonce, keyId, extUserId, teamId);
        String expectedSignature = hmacBase64(canonical, gatewayProperties.getExtAi().getHmacSecret());
        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid ext signature"));
        }

        String scopeType = hasText(teamId) ? "TEAM" : "USER";
        ServerHttpRequest.Builder builder = request.mutate();
        builder.headers(headers -> {
            headers.remove(HDR_EXT_SIGNATURE);
            headers.remove(HDR_EXT_BODY_SHA256);
            headers.remove("Authorization");
            if (hasText(extUserId)) {
                headers.set(HDR_USER, extUserId);
                headers.set(HDR_PLATFORM_USER, extUserId);
            } else {
                headers.remove(HDR_USER);
                headers.remove(HDR_PLATFORM_USER);
            }
            if (hasText(teamId)) {
                headers.set(HDR_TEAM_ID, teamId);
            } else {
                headers.remove(HDR_TEAM_ID);
            }
            headers.set(HDR_SCOPE_TYPE, scopeType);
            headers.set(HDR_GATEWAY_AUTH, gatewayProperties.getSharedSecret());
            if (hasText(extUserId)) {
                headers.set(HDR_EXT_USER_ID, extUserId);
            } else {
                headers.remove(HDR_EXT_USER_ID);
            }
            if (!hasText(headers.getFirst(HDR_CORRELATION))) {
                headers.set(HDR_CORRELATION, nonce);
            }
        });

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private static String canonicalString(
            ServerHttpRequest request,
            String bodySha256,
            String timestampRaw,
            String nonce,
            String keyId,
            String extUserId,
            String teamId
    ) {
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";
        String path = request.getURI().getPath();
        String query = request.getURI().getRawQuery() == null ? "" : request.getURI().getRawQuery();
        return String.join("\n",
                method,
                path,
                query,
                bodySha256,
                timestampRaw,
                nonce,
                keyId,
                extUserId == null ? "" : extUserId,
                teamId == null ? "" : teamId
        );
    }

    private static String requiredHeader(ServerHttpRequest request, String name) {
        String value = request.getHeaders().getFirst(name);
        if (!hasText(value)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing required header: " + name);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String normalizeBodyHash(String value) {
        if (!hasText(value)) {
            return SHA256_EMPTY_HEX;
        }
        String normalized = value.trim().toLowerCase();
        if (!normalized.matches("^[0-9a-f]{64}$")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid ext body sha256");
        }
        return normalized;
    }

    private static String hmacBase64(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to compute ext hmac", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
