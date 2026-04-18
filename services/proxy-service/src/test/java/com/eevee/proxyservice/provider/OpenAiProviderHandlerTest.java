package com.eevee.proxyservice.provider;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.TokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiProviderHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OpenAiProviderHandler handler() {
        return new OpenAiProviderHandler(objectMapper, new ProxyProperties());
    }

    @Test
    void parseUsage_supportsResponsesApiUsageShape() {
        String body = """
                {
                  "model": "gpt-5.4-nano-2026-03-17",
                  "usage": {
                    "input_tokens": 60,
                    "output_tokens": 220,
                    "total_tokens": 280,
                    "input_tokens_details": {
                      "cached_tokens": 5
                    },
                    "output_tokens_details": {
                      "reasoning_tokens": 220
                    }
                  }
                }
                """;

        TokenUsage u = handler().parseUsageFromResponseJson(body);
        assertThat(u).isNotNull();
        assertThat(u.model()).isEqualTo("gpt-5.4-nano-2026-03-17");
        assertThat(u.promptTokens()).isEqualTo(60L);
        assertThat(u.completionTokens()).isEqualTo(220L);
        assertThat(u.totalTokens()).isEqualTo(280L);
        assertThat(u.completionReasoningTokens()).isEqualTo(220L);
        assertThat(u.promptCachedTokens()).isEqualTo(5L);
    }

    /**
     * Responses API: output-side breakdown lives under {@code output_tokens_details} (not {@code completion_tokens_details}).
     * Production code uses {@code firstNode(completion_tokens_details, output_tokens_details)} — this test covers the
     * Responses-only branch so OpenAI detail fields flow into {@link TokenUsage} and then {@code provider_token_details}.
     */
    @Test
    void parseUsage_mapsAllCompletionDetailFieldsFromOutputTokensDetailsOnly() {
        String body = """
                {
                  "model": "gpt-5.4-nano",
                  "usage": {
                    "input_tokens": 5,
                    "output_tokens": 100,
                    "total_tokens": 105,
                    "output_tokens_details": {
                      "reasoning_tokens": 40,
                      "audio_tokens": 3,
                      "accepted_prediction_tokens": 2,
                      "rejected_prediction_tokens": 1
                    }
                  }
                }
                """;

        TokenUsage u = handler().parseUsageFromResponseJson(body);
        assertThat(u).isNotNull();
        assertThat(u.completionReasoningTokens()).isEqualTo(40L);
        assertThat(u.completionAudioTokens()).isEqualTo(3L);
        assertThat(u.completionAcceptedPredictionTokens()).isEqualTo(2L);
        assertThat(u.completionRejectedPredictionTokens()).isEqualTo(1L);
    }

    @Test
    void parseUsage_derivesTotalWhenTotalTokensOmitted() {
        String body = """
                {
                  "model": "gpt-5.4-nano",
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 20,
                    "input_tokens_details": { "cached_tokens": 0 },
                    "output_tokens_details": { "reasoning_tokens": 5 }
                  }
                }
                """;

        TokenUsage u = handler().parseUsageFromResponseJson(body);
        assertThat(u).isNotNull();
        assertThat(u.totalTokens()).isEqualTo(30L);
    }

    @Test
    void parseUsage_keepsChatCompletionsShapeCompatibility() {
        String body = """
                {
                  "model": "gpt-5.4-nano-2026-03-17",
                  "usage": {
                    "prompt_tokens": 22,
                    "completion_tokens": 49,
                    "total_tokens": 71,
                    "prompt_tokens_details": {
                      "cached_tokens": 3,
                      "audio_tokens": 0
                    },
                    "completion_tokens_details": {
                      "reasoning_tokens": 11,
                      "audio_tokens": 0,
                      "accepted_prediction_tokens": 1,
                      "rejected_prediction_tokens": 2
                    }
                  }
                }
                """;

        TokenUsage u = handler().parseUsageFromResponseJson(body);
        assertThat(u).isNotNull();
        assertThat(u.promptTokens()).isEqualTo(22L);
        assertThat(u.completionTokens()).isEqualTo(49L);
        assertThat(u.totalTokens()).isEqualTo(71L);
        assertThat(u.promptCachedTokens()).isEqualTo(3L);
        assertThat(u.promptAudioTokens()).isEqualTo(0L);
        assertThat(u.completionReasoningTokens()).isEqualTo(11L);
        assertThat(u.completionAudioTokens()).isEqualTo(0L);
        assertThat(u.completionAcceptedPredictionTokens()).isEqualTo(1L);
        assertThat(u.completionRejectedPredictionTokens()).isEqualTo(2L);
    }

    /**
     * Streaming: a later {@code data:} line may carry only {@code usage.total_tokens}, which must not
     * wipe prompt/completion/breakdown parsed from an earlier line.
     */
    @Test
    void parseUsageFromSse_mergesChunksSoTotalOnlyChunkDoesNotEraseDetails() {
        String sse = """
                data: {"model":"gpt-5.4-nano-2026-03-17","usage":{"input_tokens":60,"output_tokens":220,"total_tokens":280,"output_tokens_details":{"reasoning_tokens":220}}}

                data: {"usage":{"total_tokens":280}}

                """;
        TokenUsage u = handler().parseUsageFromSse(sse);
        assertThat(u).isNotNull();
        assertThat(u.promptTokens()).isEqualTo(60L);
        assertThat(u.completionTokens()).isEqualTo(220L);
        assertThat(u.totalTokens()).isEqualTo(280L);
        assertThat(u.completionReasoningTokens()).isEqualTo(220L);
    }

    @Test
    void mergeTokenUsage_prefersNonNullFieldsFromNext() {
        TokenUsage a = new TokenUsage("m1", 1L, 2L, 3L, 4L, null, null, null, null, null);
        TokenUsage b = new TokenUsage(null, null, null, 99L, null, null, null, null, null, null);
        TokenUsage m = OpenAiProviderHandler.mergeTokenUsage(a, b);
        assertThat(m.model()).isEqualTo("m1");
        assertThat(m.promptTokens()).isEqualTo(1L);
        assertThat(m.completionTokens()).isEqualTo(2L);
        assertThat(m.totalTokens()).isEqualTo(99L);
        assertThat(m.promptCachedTokens()).isEqualTo(4L);
    }
}
