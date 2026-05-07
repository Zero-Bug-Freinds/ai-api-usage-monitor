package com.eevee.usageservice.json;

import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Older proxy payloads without {@code requestSuccessful} / {@code upstreamStatusCode} must still deserialize.
 */
class LegacyUsageRecordedEventJsonTest {

    @Test
    void jsonWithoutSuccessFields_defaultsRequestSuccessful() throws Exception {
        String json = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "occurredAt": "2025-06-01T12:00:00Z",
                  "correlationId": "c",
                  "userId": "u",
                  "organizationId": null,
                  "teamId": null,
                  "provider": "OPENAI",
                  "model": "gpt-4o-mini",
                  "tokenUsage": { "model": "gpt-4o-mini", "promptTokens": 1, "completionTokens": 2, "totalTokens": 3 },
                  "estimatedCost": 0,
                  "requestPath": "/proxy/openai/v1/chat/completions",
                  "upstreamHost": "api.openai.com",
                  "streaming": false
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        UsageRecordedEvent e = mapper.readValue(json, UsageRecordedEvent.class);

        assertThat(e.requestSuccessful()).isTrue();
        assertThat(e.upstreamStatusCode()).isNull();
        assertThat(e.latencyMs()).isNull();
    }
}