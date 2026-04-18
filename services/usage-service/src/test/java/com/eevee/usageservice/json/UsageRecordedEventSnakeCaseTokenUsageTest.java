package com.eevee.usageservice.json;

import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RabbitMQ JSON may use snake_case for nested token fields (or mixed producers).
 * {@link com.eevee.usage.events.TokenUsage} accepts camelCase (proxy default) and snake_case aliases.
 */
class UsageRecordedEventSnakeCaseTokenUsageTest {

    @Test
    void snakeCaseTokenUsage_deserializesIntoTokenUsage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();

        String json = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "occurredAt": "2025-06-01T12:00:00Z",
                  "correlationId": "c",
                  "userId": "u",
                  "provider": "OPENAI",
                  "model": "gpt-5.4-nano",
                  "tokenUsage": {
                    "model": "gpt-5.4-nano",
                    "prompt_tokens": 60,
                    "completion_tokens": 220,
                    "total_tokens": 280,
                    "prompt_cached_tokens": 5,
                    "prompt_audio_tokens": 0,
                    "completion_reasoning_tokens": 220,
                    "completion_audio_tokens": 0,
                    "completion_accepted_prediction_tokens": 1,
                    "completion_rejected_prediction_tokens": 2
                  },
                  "estimatedCost": 0,
                  "requestPath": "/proxy/openai/v1/responses",
                  "upstreamHost": "api.openai.com",
                  "streaming": false,
                  "requestSuccessful": true,
                  "upstreamStatusCode": 200
                }
                """;

        UsageRecordedEvent e = mapper.readValue(json, UsageRecordedEvent.class);
        assertThat(e.tokenUsage()).isNotNull();
        assertThat(e.tokenUsage().promptTokens()).isEqualTo(60L);
        assertThat(e.tokenUsage().completionTokens()).isEqualTo(220L);
        assertThat(e.tokenUsage().totalTokens()).isEqualTo(280L);
        assertThat(e.tokenUsage().promptCachedTokens()).isEqualTo(5L);
        assertThat(e.tokenUsage().promptAudioTokens()).isEqualTo(0L);
        assertThat(e.tokenUsage().completionReasoningTokens()).isEqualTo(220L);
        assertThat(e.tokenUsage().completionAudioTokens()).isEqualTo(0L);
        assertThat(e.tokenUsage().completionAcceptedPredictionTokens()).isEqualTo(1L);
        assertThat(e.tokenUsage().completionRejectedPredictionTokens()).isEqualTo(2L);
    }

    @Test
    void token_usageAlias_deserializesTokenUsage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();

        String json = """
                {
                  "eventId": "22222222-2222-2222-2222-222222222222",
                  "occurredAt": "2025-06-01T12:00:00Z",
                  "correlationId": "c",
                  "userId": "u",
                  "provider": "OPENAI",
                  "model": "gpt-4o-mini",
                  "token_usage": {
                    "model": "gpt-4o-mini",
                    "promptTokens": 1,
                    "completionTokens": 2,
                    "totalTokens": 3
                  },
                  "estimatedCost": 0,
                  "requestPath": "/proxy/openai/v1/chat/completions",
                  "upstreamHost": "api.openai.com",
                  "streaming": false,
                  "requestSuccessful": true,
                  "upstreamStatusCode": 200
                }
                """;

        UsageRecordedEvent e = mapper.readValue(json, UsageRecordedEvent.class);
        assertThat(e.tokenUsage()).isNotNull();
        assertThat(e.tokenUsage().totalTokens()).isEqualTo(3L);
    }
}
