package com.eevee.usageservice.json;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * proxy-service의 {@link com.eevee.proxyservice.config.JacksonConfiguration}과 동일하게
 * {@code new ObjectMapper().findAndRegisterModules()} 로 직렬화한 JSON을
 * usage-service가 역직렬화할 수 있는지 검증한다.
 */
class UsageRecordedEventWireFormatTest {

    @Test
    void proxyStyleObjectMapper_roundTripsUsageRecordedEvent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();

        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent original = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-06-01T12:00:00Z"),
                "corr-wire",
                "user-wire",
                "org-1",
                "team-1",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 5L, 7L, 12L),
                new BigDecimal("0.0012"),
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                true,
                200
        );

        String json = mapper.writeValueAsString(original);
        UsageRecordedEvent read = mapper.readValue(json, UsageRecordedEvent.class);

        assertThat(read.eventId()).isEqualTo(original.eventId());
        assertThat(read.userId()).isEqualTo("user-wire");
        assertThat(read.provider()).isEqualTo(AiProvider.OPENAI);
        assertThat(read.tokenUsage()).isNotNull();
        assertThat(read.tokenUsage().totalTokens()).isEqualTo(12L);
    }
}
