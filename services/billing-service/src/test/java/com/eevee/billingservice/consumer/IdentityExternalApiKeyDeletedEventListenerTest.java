package com.eevee.billingservice.consumer;

import com.eevee.billingservice.service.PersonalExternalApiKeyExpenditurePurgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IdentityExternalApiKeyDeletedEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private PersonalExternalApiKeyExpenditurePurgeService purgeService;

    private IdentityExternalApiKeyDeletedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new IdentityExternalApiKeyDeletedEventListener(objectMapper, purgeService);
    }

    @Test
    void budgetChanged_doesNotPurge() {
        listener.onMessage("{\"eventType\":\"EXTERNAL_API_KEY_BUDGET_CHANGED\",\"userId\":\"a@b.com\",\"apiKeyId\":1}");
        verifyNoInteractions(purgeService);
    }

    @Test
    void schemaVersionEnvelope_doesNotPurge() {
        listener.onMessage("{\"schemaVersion\":1,\"apiKeyId\":1}");
        verifyNoInteractions(purgeService);
    }

    @Test
    void deletedEvent_invokesPurge() throws Exception {
        ExternalApiKeyDeletedEvent ev = ExternalApiKeyDeletedEvent.of(
                "u@e.com",
                99L,
                Instant.parse("2026-05-01T00:00:00Z"),
                true,
                "OPENAI",
                "n"
        );
        listener.onMessage(objectMapper.writeValueAsString(ev));
        verify(purgeService).purgeForDeletedExternalApiKey(eq("u@e.com"), eq(99L));
    }
}
