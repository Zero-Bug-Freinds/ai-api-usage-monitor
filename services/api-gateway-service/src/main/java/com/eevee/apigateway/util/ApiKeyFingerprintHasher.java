package com.eevee.apigateway.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * SHA-256 fingerprint of UTF-8 plain API key text (64 lowercase hex chars).
 * Must match identity {@code EncryptionUtil#sha256HexUtf8} used for {@code api_key_fingerprint}.
 */
public final class ApiKeyFingerprintHasher {

    private ApiKeyFingerprintHasher() {
    }

    public static String sha256HexUtf8(String utf8Text) {
        if (utf8Text == null) {
            throw new IllegalArgumentException("utf8Text is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(utf8Text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
