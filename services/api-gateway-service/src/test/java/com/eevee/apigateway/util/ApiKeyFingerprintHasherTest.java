package com.eevee.apigateway.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFingerprintHasherTest {

    @Test
    void sha256HexUtf8_matchesKnownVector() {
        assertThat(ApiKeyFingerprintHasher.sha256HexUtf8("test"))
                .isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    void sha256HexUtf8_isLowercase64Hex() {
        String fingerprint = ApiKeyFingerprintHasher.sha256HexUtf8("sk-test-key");
        assertThat(fingerprint).hasSize(64);
        assertThat(fingerprint).isEqualTo(fingerprint.toLowerCase());
    }
}
