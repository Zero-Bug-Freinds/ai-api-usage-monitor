package com.eevee.billingservice.consumer;

import com.eevee.billingservice.event.TeamDomainAmqpEventTypes;
import com.eevee.billingservice.service.TeamApiKeyExpenditurePurgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TeamApiKeyDeletedEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private TeamApiKeyExpenditurePurgeService purgeService;

    private TeamApiKeyDeletedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TeamApiKeyDeletedEventListener(objectMapper, purgeService);
    }

    @Test
    void registeredEvent_doesNotPurge() {
        listener.onMessage("{\"eventType\":\"TEAM_API_KEY_REGISTERED\",\"apiKeyId\":1,\"teamId\":\"1\"}");
        verifyNoInteractions(purgeService);
    }

    @Test
    void envelopeWithoutEventType_doesNotPurge() {
        listener.onMessage("{\"apiKeyId\":1}");
        verifyNoInteractions(purgeService);
    }

    @Test
    void deletedEvent_invokesPurge() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "eventType", TeamDomainAmqpEventTypes.TEAM_API_KEY_DELETED,
                "teamId", "9",
                "teamName", "n",
                "actorUserId", "a",
                "occurredAt", Instant.parse("2026-05-01T00:00:00Z"),
                "recipientUserIds", java.util.List.of(),
                "apiKeyId", 42L,
                "retainLogs", false,
                "provider", "OPENAI",
                "alias", "k"
        ));
        listener.onMessage(json);
        verify(purgeService).purgeForDeletedTeamApiKey(eq(42L));
    }
}
