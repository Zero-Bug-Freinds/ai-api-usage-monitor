package com.eevee.proxyservice.key;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyFingerprintNormalizerTest {

    @Test
    void fromHeaders_prefersFingerprintHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyFingerprintNormalizer.HDR_API_KEY_FINGERPRINT,
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");

        assertThat(ApiKeyFingerprintNormalizer.fromHeadersOrRawKey(headers, "sk-other"))
                .isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    void fromRawKey_hashesWhenHeaderAbsent() {
        assertThat(ApiKeyFingerprintNormalizer.fromHeadersOrRawKey(new HttpHeaders(), "test"))
                .isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    void rejectsInvalidFingerprint() {
        assertThatThrownBy(() -> ApiKeyFingerprintNormalizer.normalizeFingerprintHex("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
