package com.eevee.proxyservice.identity;

import java.util.Locale;

/**
 * Normalizes usage-event user subjects to match Identity JWT {@code sub} (lowercase email).
 */
public final class UsageSubjectNormalizer {

    private static final String USER_PREFIX = "u_";

    private UsageSubjectNormalizer() {
    }

    public static boolean looksLikeEmail(String value) {
        return value != null && value.contains("@");
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Strips {@code u_<digits>} opaque owner ids from fingerprint lookup to a numeric PK string.
     */
    public static String stripOpaqueUserPrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.regionMatches(true, 0, USER_PREFIX, 0, USER_PREFIX.length())) {
            String suffix = trimmed.substring(USER_PREFIX.length()).trim();
            if (suffix.matches("\\d+")) {
                return suffix;
            }
        }
        return trimmed;
    }

    public static String normalizeSubject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (looksLikeEmail(raw)) {
            return normalizeEmail(raw);
        }
        return raw.trim();
    }
}
