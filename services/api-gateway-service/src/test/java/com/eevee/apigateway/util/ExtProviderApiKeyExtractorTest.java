package com.eevee.apigateway.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtProviderApiKeyExtractorTest {

    @Test
    void extractPlainKey_prefersExtRawOverXApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ExtProviderApiKeyExtractor.HDR_EXT_RAW_API_KEY, "  raw-key  ");
        headers.set(ExtProviderApiKeyExtractor.HDR_X_API_KEY, "x-api-key");

        assertThat(ExtProviderApiKeyExtractor.extractPlainKey(headers)).contains("raw-key");
    }

    @Test
    void extractPlainKey_prefersXApiKeyOverGoog() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ExtProviderApiKeyExtractor.HDR_X_API_KEY, "sk-openai");
        headers.set(ExtProviderApiKeyExtractor.HDR_X_GOOG_API_KEY, "AIza-goog");

        assertThat(ExtProviderApiKeyExtractor.extractPlainKey(headers)).contains("sk-openai");
    }

    @Test
    void extractPlainKey_usesProviderBearerWhenNoKeyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ExtProviderApiKeyExtractor.HDR_AUTHORIZATION, "Bearer sk-secret");

        assertThat(ExtProviderApiKeyExtractor.extractPlainKey(headers)).contains("sk-secret");
    }

    @Test
    void extractPlainKey_ignoresNonProviderBearer() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(
                ExtProviderApiKeyExtractor.HDR_AUTHORIZATION,
                "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig"
        );

        assertThat(ExtProviderApiKeyExtractor.extractPlainKey(headers)).isEmpty();
    }

    @Test
    void extractPlainKey_blankHeaderIsIgnored() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ExtProviderApiKeyExtractor.HDR_X_API_KEY, "   ");

        assertThat(ExtProviderApiKeyExtractor.extractPlainKey(headers)).isEmpty();
    }

    @Test
    void extractPlainKey_rejectsOverlongKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ExtProviderApiKeyExtractor.HDR_X_API_KEY, "sk-" + "a".repeat(8192));

        assertThatThrownBy(() -> ExtProviderApiKeyExtractor.extractPlainKey(headers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum length");
    }
}
