package com.eevee.apigateway.filter;

import com.eevee.apigateway.config.GatewayProperties;
import com.eevee.apigateway.util.ApiKeyFingerprintHasher;
import com.eevee.apigateway.util.ExtAiProviderPath;
import com.eevee.apigateway.util.ExtProviderApiKeyExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Ext ingress: derive {@code X-Api-Key-Fingerprint} from client provider key headers and strip raw key material.
 */
public class ExtAiApiKeyFingerprintWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ExtAiApiKeyFingerprintWebFilter.class);

    private static final String HDR_EXT_KEY_ID = "X-Ext-Key-Id";
    private static final String HDR_EXT_TIMESTAMP = "X-Ext-Timestamp";
    private static final String HDR_EXT_NONCE = "X-Ext-Nonce";
    private static final String HDR_EXT_BODY_SHA256 = "X-Ext-Body-Sha256";
    private static final String HDR_EXT_SIGNATURE = "X-Ext-Signature";
    private static final String HDR_CORRELATION = "X-Correlation-Id";

    private final GatewayProperties gatewayProperties;

    public ExtAiApiKeyFingerprintWebFilter(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!ExtAiProviderPath.isExtAiPath(path)) {
            return chain.filter(exchange);
        }
        if (!gatewayProperties.getExtAi().isEnabled()) {
            return chain.filter(exchange);
        }

        final String providerEnumName;
        try {
            providerEnumName = ExtAiProviderPath.providerEnumNameFromPath(path);
        } catch (IllegalArgumentException ex) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()));
        }

        ServerHttpRequest request = exchange.getRequest();
        Optional<String> plainKey;
        try {
            plainKey = ExtProviderApiKeyExtractor.extractPlainKey(request.getHeaders());
        } catch (IllegalArgumentException ex) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()));
        }

        if (plainKey.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("ext path has no provider api key header path={} provider={}", path, providerEnumName);
            }
            return chain.filter(exchange);
        }

        String fingerprint = ApiKeyFingerprintHasher.sha256HexUtf8(plainKey.get());
        GatewayProperties.ExtAi extAi = gatewayProperties.getExtAi();
        String fingerprintHeader = extAi.getFingerprintHeader();
        String providerHeader = extAi.getProviderHeader();

        ServerHttpRequest.Builder builder = request.mutate();
        builder.headers(headers -> {
            headers.set(fingerprintHeader, fingerprint);
            headers.set(providerHeader, providerEnumName);
            headers.remove(ExtProviderApiKeyExtractor.HDR_AUTHORIZATION);
            headers.remove(ExtProviderApiKeyExtractor.HDR_X_API_KEY);
            headers.remove(ExtProviderApiKeyExtractor.HDR_X_GOOG_API_KEY);
            headers.remove(ExtProviderApiKeyExtractor.HDR_EXT_RAW_API_KEY);
            headers.remove(HDR_EXT_SIGNATURE);
            headers.remove(HDR_EXT_BODY_SHA256);
            headers.remove(HDR_EXT_KEY_ID);
            headers.remove(HDR_EXT_TIMESTAMP);
            headers.remove(HDR_EXT_NONCE);
        });

        if (log.isInfoEnabled()) {
            String correlation = request.getHeaders().getFirst(HDR_CORRELATION);
            log.info(
                    "ext fingerprint prepared provider={} fingerprintPrefix={} correlationId={}",
                    providerEnumName,
                    fingerprintPrefix(fingerprint, extAi.getFingerprintLogPrefixLength()),
                    correlation != null ? correlation : "-"
            );
        }

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private static String fingerprintPrefix(String fingerprint, int length) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return "-";
        }
        int keep = Math.min(Math.max(length, 6), fingerprint.length());
        return fingerprint.substring(0, keep);
    }
}
