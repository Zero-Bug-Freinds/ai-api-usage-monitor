package com.eevee.proxyservice.provider;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.TokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleProviderHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GoogleProviderHandler handler() {
        return new GoogleProviderHandler(objectMapper, new ProxyProperties());
    }

    @Test
    void parseUsage_prefersModelVersionWhenModelKeyAbsent() {
        String body = """
                {
                  "candidates": [],
                  "modelVersion": "gemini-2.5-flash-lite",
                  "usageMetadata": {
                    "promptTokenCount": 10,
                    "candidatesTokenCount": 5,
                    "totalTokenCount": 15
                  }
                }
                """;
        TokenUsage u = handler().parseUsageFromResponseJson(body);
        assertThat(u).isNotNull();
        assertThat(u.model()).isEqualTo("gemini-2.5-flash-lite");
        assertThat(u.promptTokens()).isEqualTo(10L);
        assertThat(u.completionTokens()).isEqualTo(5L);
        assertThat(u.totalTokens()).isEqualTo(15L);
    }

    @Test
    void parseUsage_fallsBackToModelWhenModelVersionMissing() {
        String body = """
                {
                  "model": "gemini-pro-legacy",
                  "usageMetadata": {
                    "promptTokenCount": 1,
                    "candidatesTokenCount": 2,
                    "totalTokenCount": 3
                  }
                }
                """;
        TokenUsage u = handler().parseUsageFromResponseJson(body);
        assertThat(u).isNotNull();
        assertThat(u.model()).isEqualTo("gemini-pro-legacy");
    }

    @Test
    void parseUsage_prefersModelVersionWhenBothPresent() {
        String body = """
                {
                  "modelVersion": "gemini-2.5-flash-lite",
                  "model": "ignored",
                  "usageMetadata": {
                    "promptTokenCount": 1,
                    "candidatesTokenCount": 0,
                    "totalTokenCount": 1
                  }
                }
                """;
        TokenUsage u = handler().parseUsageFromResponseJson(body);
        assertThat(u).isNotNull();
        assertThat(u.model()).isEqualTo("gemini-2.5-flash-lite");
    }

    @Test
    void parseUsage_returnsNullWhenUsageMetadataMissing() {
        String body = "{\"modelVersion\": \"x\"}";
        assertThat(handler().parseUsageFromResponseJson(body)).isNull();
    }
}
