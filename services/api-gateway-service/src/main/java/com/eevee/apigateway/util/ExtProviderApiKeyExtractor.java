package com.eevee.apigateway.util;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads provider API key material from ext ingress headers (never from request body).
 */
public final class ExtProviderApiKeyExtractor {

    public static final String HDR_EXT_RAW_API_KEY = "X-Ext-Raw-Api-Key";
    public static final String HDR_X_API_KEY = "X-Api-Key";
    public static final String HDR_X_GOOG_API_KEY = "X-Goog-Api-Key";
    public static final String HDR_AUTHORIZATION = "Authorization";

    private static final int MAX_KEY_LENGTH = 8_192;
    private static final Pattern PROVIDER_KEY_HEURISTIC = Pattern.compile(
            "^(sk-|sk-proj-|AIza|xai-|anthropic-|rk-|gsk_)"
    );

    private ExtProviderApiKeyExtractor() {
    }

    /**
     * @return trimmed plain key if present in allowed headers
     */
    public static Optional<String> extractPlainKey(HttpHeaders headers) {
        if (headers == null) {
            return Optional.empty();
        }
        String fromExtRaw = firstNonBlank(headers, HDR_EXT_RAW_API_KEY);
        if (fromExtRaw != null) {
            return Optional.of(validateLength(fromExtRaw));
        }
        String fromXApiKey = firstNonBlank(headers, HDR_X_API_KEY);
        if (fromXApiKey != null) {
            return Optional.of(validateLength(fromXApiKey));
        }
        String fromGoog = firstNonBlank(headers, HDR_X_GOOG_API_KEY);
        if (fromGoog != null) {
            return Optional.of(validateLength(fromGoog));
        }
        String bearer = extractProviderBearer(headers.getFirst(HDR_AUTHORIZATION));
        if (bearer != null) {
            return Optional.of(validateLength(bearer));
        }
        return Optional.empty();
    }

    static String extractProviderBearer(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String trimmed = authorization.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = trimmed.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (!looksLikeProviderApiKey(token)) {
            return null;
        }
        return token;
    }

    static boolean looksLikeProviderApiKey(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        return PROVIDER_KEY_HEURISTIC.matcher(token).find();
    }

    private static String validateLength(String key) {
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("api key material is empty");
        }
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("api key material exceeds maximum length");
        }
        return trimmed;
    }

    private static String firstNonBlank(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
