package com.eevee.usageservice.consumer;

import com.zerobugfreinds.identity.events.IdentityExternalApiKeyEventTypes;
import com.eevee.usageservice.service.ApiKeyMetadataSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExternalApiKeyStatusChangedEventListenerTest {

    @Mock
    private ApiKeyMetadataSyncService apiKeyMetadataSyncService;

    private ExternalApiKeyStatusChangedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ExternalApiKeyStatusChangedEventListener(new ObjectMapper(), apiKeyMetadataSyncService);
    }

    @Test
    void ignoresBudgetChangedPayload() {
        listener.onMessage("""
                {
                  "eventType": "%s",
                  "schemaVersion": 1,
                  "occurredAt": "2026-05-11T10:00:00Z",
                  "keyId": 1,
                  "alias": "x",
                  "userId": 2,
                  "visibility": "PRIVATE",
                  "provider": "GOOGLE",
                  "status": "ACTIVE",
                  "monthlyBudgetUsd": 1.0,
                  "keyHash": "h"
                }
                """.formatted(IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_BUDGET_CHANGED));

        verify(apiKeyMetadataSyncService, times(0)).upsertFromIdentity(any());
        verify(apiKeyMetadataSyncService, times(0)).handleExternalApiKeyDeleted(any());
    }

    @Test
    void ignoresUserContextChangedPayload() {
        listener.onMessage("""
                {
                  "eventType": "%s",
                  "schemaVersion": 1,
                  "occurredAt": "2026-05-11T10:00:00Z",
                  "userId": 3,
                  "activeTeamId": 9,
                  "role": "MEMBER"
                }
                """.formatted(IdentityExternalApiKeyEventTypes.USER_CONTEXT_CHANGED));

        verify(apiKeyMetadataSyncService, times(0)).upsertFromIdentity(any());
        verify(apiKeyMetadataSyncService, times(0)).handleExternalApiKeyDeleted(any());
    }

    @Test
    void malformedJson_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.onMessage("{not-json"))
                .isInstanceOf(IllegalStateException.class);
    }
}
