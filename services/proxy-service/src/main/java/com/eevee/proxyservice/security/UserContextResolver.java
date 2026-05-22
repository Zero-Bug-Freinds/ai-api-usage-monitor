package com.eevee.proxyservice.security;

import com.eevee.proxyservice.key.ApiKeyFingerprintNormalizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class UserContextResolver {

    private static final String HDR_USER = "X-User-Id";
    private static final String HDR_PLATFORM_USER = "X-Platform-User-Id";
    private static final String HDR_ORG = "X-Org-Id";
    private static final String HDR_TEAM = "X-Team-Id";
    private static final String HDR_CORRELATION = "X-Correlation-Id";
    private static final String HDR_API_KEY_ID = "X-Api-Key-Id";
    private static final String HDR_API_KEY_ALIAS = "X-Api-Key-Alias";
    private static final String HDR_EXT_USER_ID = "X-Ext-User-Id";
    private static final String HDR_EXT_RAW_API_KEY = "X-Ext-Raw-Api-Key";
    private static final String HDR_X_API_KEY = "X-Api-Key";
    private static final String HDR_X_GOOG_API_KEY = "X-Goog-Api-Key";

    public Mono<UserContext> fromExchange(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HDR_CORRELATION);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> mapAuthentication(ctx.getAuthentication(), exchange, finalCorrelationId))
                .switchIfEmpty(Mono.defer(() -> mapAuthentication(null, exchange, finalCorrelationId)));
    }

    private Mono<UserContext> mapAuthentication(
            Authentication auth,
            ServerWebExchange exchange,
            String correlationId
    ) {
        String platformUserId = firstNonBlankHeader(exchange, HDR_PLATFORM_USER);
        String requestedApiKeyId = firstNonBlankHeader(exchange, HDR_API_KEY_ID);
        String requestedApiKeyAlias = firstNonBlankHeader(exchange, HDR_API_KEY_ALIAS);
        String extUserId = firstNonBlankHeader(exchange, HDR_EXT_USER_ID);
        String rawApiKey = firstRawApiKey(exchange);
        String apiKeyFingerprint64 = resolveFingerprint64(exchange, rawApiKey);

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String userId) {
            return Mono.just(buildContext(
                    userId,
                    platformUserId,
                    exchange,
                    correlationId,
                    requestedApiKeyId,
                    requestedApiKeyAlias,
                    extUserId,
                    rawApiKey,
                    apiKeyFingerprint64
            ));
        }
        String userId = exchange.getRequest().getHeaders().getFirst(HDR_USER);
        if (userId != null && !userId.isBlank()) {
            return Mono.just(buildContext(
                    userId,
                    platformUserId,
                    exchange,
                    correlationId,
                    requestedApiKeyId,
                    requestedApiKeyAlias,
                    extUserId,
                    rawApiKey,
                    apiKeyFingerprint64
            ));
        }
        if (rawApiKey != null || apiKeyFingerprint64 != null) {
            String org = exchange.getRequest().getHeaders().getFirst(HDR_ORG);
            String team = exchange.getRequest().getHeaders().getFirst(HDR_TEAM);
            return Mono.just(new UserContext(
                    extUserId,
                    null,
                    org,
                    team,
                    correlationId,
                    requestedApiKeyId,
                    requestedApiKeyAlias,
                    extUserId,
                    rawApiKey,
                    apiKeyFingerprint64
            ));
        }
        return Mono.error(new IllegalStateException("Missing X-User-Id (from Gateway)"));
    }

    private static UserContext buildContext(
            String userId,
            String platformUserId,
            ServerWebExchange exchange,
            String correlationId,
            String requestedApiKeyId,
            String requestedApiKeyAlias,
            String extUserId,
            String rawApiKey,
            String apiKeyFingerprint64
    ) {
        String org = exchange.getRequest().getHeaders().getFirst(HDR_ORG);
        String team = exchange.getRequest().getHeaders().getFirst(HDR_TEAM);
        return new UserContext(
                userId,
                platformUserId,
                org,
                team,
                correlationId,
                requestedApiKeyId,
                requestedApiKeyAlias,
                extUserId,
                rawApiKey,
                apiKeyFingerprint64
        );
    }

    private static String resolveFingerprint64(ServerWebExchange exchange, String rawApiKey) {
        try {
            return ApiKeyFingerprintNormalizer.fromHeadersOrRawKey(exchange.getRequest().getHeaders(), rawApiKey);
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    ex.getMessage()
            );
        }
    }

    private static String firstNonBlankHeader(ServerWebExchange exchange, String name) {
        String v = exchange.getRequest().getHeaders().getFirst(name);
        return (v != null && !v.isBlank()) ? v : null;
    }

    private static String firstRawApiKey(ServerWebExchange exchange) {
        String ext = firstNonBlankHeader(exchange, HDR_EXT_RAW_API_KEY);
        if (ext != null) {
            return ext;
        }
        String xApiKey = firstNonBlankHeader(exchange, HDR_X_API_KEY);
        if (xApiKey != null) {
            return xApiKey;
        }
        return firstNonBlankHeader(exchange, HDR_X_GOOG_API_KEY);
    }
}
