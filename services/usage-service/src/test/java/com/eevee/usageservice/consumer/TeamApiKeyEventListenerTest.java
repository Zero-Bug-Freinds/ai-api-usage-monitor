package com.eevee.usageservice.consumer;

import com.eevee.usageservice.mq.TeamApiKeyDeletedEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionCancelledEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionScheduledEvent;
import com.eevee.usageservice.mq.TeamApiKeyRegisteredEvent;
import com.eevee.usageservice.mq.TeamApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.TeamApiKeyUpdatedEvent;
import com.eevee.usageservice.service.ApiKeyMetadataSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TeamApiKeyEventListenerTest {

    @Mock
    private ApiKeyMetadataSyncService apiKeyMetadataSyncService;

    private TeamApiKeyEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TeamApiKeyEventListener(new ObjectMapper(), apiKeyMetadataSyncService);
    }

    @Test
    void routesRegisteredEvent() {
        listener.onMessage("""
                {"eventType":"TEAM_API_KEY_REGISTERED","apiKeyId":1,"actorUserId":"owner","provider":"OPENAI","alias":"a"}
                """);
        verify(apiKeyMetadataSyncService).upsertFromTeamRegistered(any(TeamApiKeyRegisteredEvent.class));
    }

    @Test
    void routesUpdatedEvent() {
        listener.onMessage("""
                {"eventType":"TEAM_API_KEY_UPDATED","apiKeyId":2,"actorUserId":"owner","provider":"OPENAI","alias":"a"}
                """);
        verify(apiKeyMetadataSyncService).upsertFromTeamUpdated(any(TeamApiKeyUpdatedEvent.class));
    }

    @Test
    void routesDeletedEvent() {
        listener.onMessage("""
                {"eventType":"TEAM_API_KEY_DELETED","apiKeyId":3,"actorUserId":"owner","provider":"OPENAI","alias":"a"}
                """);
        verify(apiKeyMetadataSyncService).handleTeamDeleted(any(TeamApiKeyDeletedEvent.class));
    }

    @Test
    void routesDeletionScheduledEvent() {
        listener.onMessage("""
                {"eventType":"TEAM_API_KEY_DELETION_SCHEDULED","apiKeyId":4,"actorUserId":"owner","provider":"OPENAI","alias":"a"}
                """);
        verify(apiKeyMetadataSyncService).handleTeamDeletionScheduled(any(TeamApiKeyDeletionScheduledEvent.class));
    }

    @Test
    void routesDeletionCancelledEvent() {
        listener.onMessage("""
                {"eventType":"TEAM_API_KEY_DELETION_CANCELLED","apiKeyId":5,"actorUserId":"owner","provider":"OPENAI","alias":"a"}
                """);
        verify(apiKeyMetadataSyncService).handleTeamDeletionCancelled(any(TeamApiKeyDeletionCancelledEvent.class));
    }

    @Test
    void routesStatusChangedEvent() {
        listener.onMessage("""
                {"eventType":"TEAM_API_KEY_STATUS_CHANGED","teamApiKeyId":6,"ownerUserId":"owner","provider":"OPENAI","alias":"a","status":"ACTIVE"}
                """);
        verify(apiKeyMetadataSyncService).upsertFromTeamStatusChanged(any(TeamApiKeyStatusChangedEvent.class));
    }

    @Test
    void ignoresUnknownEventType() {
        listener.onMessage("""
                {"eventType":"TEAM_CREATED","teamId":"1"}
                """);
        verifyNoInteractions(apiKeyMetadataSyncService);
    }

    @Test
    void throwsOnMalformedJson() {
        assertThatThrownBy(() -> listener.onMessage("{not-json"))
                .isInstanceOf(IllegalStateException.class);
    }
}
