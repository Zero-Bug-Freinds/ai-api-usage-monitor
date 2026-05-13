package com.eevee.usageservice.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatus;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityExternalApiKeyEventUnknownFieldTest {

    @Test
    void deserializesIdentityPayloadWithExtraFieldsAndStringUserId() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = """
                {
                  "schemaVersion": 2,
                  "occurredAt": "2026-01-15T10:00:00Z",
                  "keyId": 99,
                  "alias": "k",
                  "userId": "owner@example.com",
                  "visibility": "PRIVATE",
                  "provider": "OPENAI",
                  "status": "ACTIVE",
                  "keyHash": "extra-from-identity",
                  "futureField": true
                }
                """;
        ExternalApiKeyStatusChangedEvent event = mapper.readValue(json, ExternalApiKeyStatusChangedEvent.class);
        assertThat(event.keyId()).isEqualTo(99L);
        assertThat(event.userId()).isEqualTo("owner@example.com");
        assertThat(event.status()).isEqualTo(ExternalApiKeyStatus.ACTIVE);
    }
}
