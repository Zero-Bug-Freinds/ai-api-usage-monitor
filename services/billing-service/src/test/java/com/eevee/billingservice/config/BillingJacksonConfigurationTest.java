package com.eevee.billingservice.config;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BillingJacksonConfiguration.class)
class BillingJacksonConfigurationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deserializesProxyStyleUsageRecordedEventJson() throws Exception {
        String json = """
                {
                  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "occurredAt": "2026-05-23T01:39:11.441924Z",
                  "correlationId": "corr-proxy",
                  "userId": "user@example.com",
                  "provider": "GOOGLE",
                  "model": "gemini-2.0-flash",
                  "tokenUsage": {
                    "model": "gemini-2.0-flash",
                    "promptTokens": 10,
                    "completionTokens": 20,
                    "totalTokens": 30
                  },
                  "estimatedCost": 0,
                  "requestPath": "/proxy/google/v1/models/gemini-2.0-flash:generateContent",
                  "upstreamHost": "generativelanguage.googleapis.com",
                  "latencyMs": 512,
                  "streaming": false,
                  "requestSuccessful": true,
                  "upstreamStatusCode": 200,
                  "metadataOwnerUserId": "3"
                }
                """;

        UsageRecordedEvent event = objectMapper.readValue(json, UsageRecordedEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-23T01:39:11.441924Z"));
        assertThat(event.userId()).isEqualTo("user@example.com");
        assertThat(event.provider()).isEqualTo(AiProvider.GOOGLE);
        assertThat(event.metadataOwnerUserId()).isEqualTo("3");
    }
}
