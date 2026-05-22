package com.eevee.proxyservice.key;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Normalizes 64-char SHA-256 fingerprint from gateway header or raw provider key material.
 */
public final class ApiKeyFingerprintNormalizer {

    public static final String HDR_API_KEY_FINGERPRINT = "X-Api-Key-Fingerprint";
    public static final String HDR_AI_PROVIDER = "X-Ai-Provider";

    private ApiKeyFingerprintNormalizer() {
    }

    public static String fromHeadersOrRawKey(HttpHeaders headers, String rawApiKey) {
        String fromHeader = headers != null ? headers.getFirst(HDR_API_KEY_FINGERPRINT) : null;
        if (StringUtils.hasText(fromHeader)) {
            return normalizeFingerprintHex(fromHeader);
        }
        if (StringUtils.hasText(rawApiKey)) {
            return sha256HexUtf8(rawApiKey.trim());
        }
        return null;
    }

    public static String normalizeFingerprintHex(String fingerprint) {
        if (!StringUtils.hasText(fingerprint)) {
            throw new IllegalArgumentException("fingerprint is required");
        }
        String trimmed = fingerprint.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint must be 64 lowercase hex characters");
        }
        return trimmed;
    }

    public static String sha256HexUtf8(String utf8Text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(utf8Text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String fingerprintPrefix(String fingerprint64, int length) {
        if (!StringUtils.hasText(fingerprint64)) {
            return "-";
        }
        int keep = Math.min(Math.max(length, 6), fingerprint64.length());
        return fingerprint64.substring(0, keep);
    }
}
