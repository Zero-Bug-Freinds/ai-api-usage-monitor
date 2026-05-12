package com.eevee.usageservice.json;

import com.eevee.usageservice.mq.ExternalApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityExternalApiKeyEventUnknownFieldTest {

    @Test
    void deserializesWhenIdentityAddsKeyHash() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                true
        );
        String json = """
                {
                  "schemaVersion": 1,
                  "occurredAt": "2026-01-15T10:00:00Z",
                  "keyId": 99,
                  "alias": "k",
                  "userId": 1,
                  "provider": "openai",
                  "status": "ACTIVE",
                  "keyHash": "extra-from-identity"
                }
                """;
        ExternalApiKeyStatusChangedEvent event = mapper.readValue(json, ExternalApiKeyStatusChangedEvent.class);
        assertThat(event.keyId()).isEqualTo(99L);
        assertThat(event.status()).isEqualTo(ExternalApiKeyStatus.ACTIVE);
    }
}
