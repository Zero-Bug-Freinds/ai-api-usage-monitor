package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageRecordedServiceTest {

    @Mock
    private UsageRecordedLogRepository repository;

    private UsageRecordedService usageRecordedService;

    @BeforeEach
    void setUp() {
        usageRecordedService = new UsageRecordedService(repository, new ObjectMapper());
    }

    @Test
    void skipsPersistWhenEventIdAlreadyExists() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-01-01T00:00:00Z"),
                "corr-1",
                "user-1",
                null,
                null,
                "key-1",
                "deadbeef00112233",
                "managed",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 10L, 20L, 30L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                true,
                200
        );

        when(repository.existsByEventId(eventId)).thenReturn(true);

        usageRecordedService.persist(event);

        verify(repository, never()).save(any());
    }

    @Test
    void savesWhenNewEventId() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-01-01T00:00:00Z"),
                "corr-1",
                "user-1",
                null,
                null,
                "key-1",
                "deadbeef00112233",
                "managed",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 10L, 20L, 30L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                true,
                200
        );

        when(repository.existsByEventId(eventId)).thenReturn(false);

        usageRecordedService.persist(event);

        ArgumentCaptor<UsageRecordedLogEntity> captor = ArgumentCaptor.forClass(UsageRecordedLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(captor.getValue().getApiKeyId()).isEqualTo("key-1");
    }

    @Test
    void openAi_reasoningTokens_areDerivedFromDetailedFields() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-01-01T00:00:00Z"),
                "corr-1",
                "user-1",
                null,
                null,
                "key-1",
                "deadbeef00112233",
                "managed",
                AiProvider.OPENAI,
                "gpt-5.4-mini",
                new TokenUsage("gpt-5.4-mini", 10L, 20L, 50L, 1L, 2L, 11L, 3L, 5L, 7L),
                BigDecimal.ZERO,
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                true,
                200
        );
        when(repository.existsByEventId(eventId)).thenReturn(false);

        usageRecordedService.persist(event);

        ArgumentCaptor<UsageRecordedLogEntity> captor = ArgumentCaptor.forClass(UsageRecordedLogEntity.class);
        verify(repository).save(captor.capture());
        // OPENAI = reasoning + audio + accepted + rejected
        assertThat(captor.getValue().getEstimatedReasoningTokens()).isEqualTo(26L);
    }

    @Test
    void openAi_whenNoBreakdownDetails_estimatesReasoningFromTotalMinusPromptMinusCompletion() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-01-01T00:00:00Z"),
                "corr-1",
                "user-1",
                null,
                null,
                "key-1",
                "deadbeef00112233",
                "managed",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 10L, 20L, 50L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                true,
                200
        );
        when(repository.existsByEventId(eventId)).thenReturn(false);

        usageRecordedService.persist(event);

        ArgumentCaptor<UsageRecordedLogEntity> captor = ArgumentCaptor.forClass(UsageRecordedLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEstimatedReasoningTokens()).isEqualTo(20L);
    }

    @Test
    void openAi_whenNoBreakdownAndIncompleteTokenUsage_estimatedReasoningIsNull() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-01-01T00:00:00Z"),
                "corr-1",
                "user-1",
                null,
                null,
                "key-1",
                "deadbeef00112233",
                "managed",
                AiProvider.OPENAI,
                "gpt-5.4-nano",
                new TokenUsage("gpt-5.4-nano", null, null, 280L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/openai/v1/responses",
                "api.openai.com",
                false,
                true,
                200
        );
        when(repository.existsByEventId(eventId)).thenReturn(false);

        usageRecordedService.persist(event);

        ArgumentCaptor<UsageRecordedLogEntity> captor = ArgumentCaptor.forClass(UsageRecordedLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEstimatedReasoningTokens()).isNull();
    }

    @Test
    void google_reasoningTokens_keepEstimateFormula() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-01-01T00:00:00Z"),
                "corr-1",
                "user-1",
                null,
                null,
                "key-1",
                "deadbeef00112233",
                "managed",
                AiProvider.GOOGLE,
                "gemini-2.5-flash",
                new TokenUsage("gemini-2.5-flash", 10L, 20L, 50L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/google/v1beta/models/gemini-2.5-flash:generateContent",
                "generativelanguage.googleapis.com",
                false,
                true,
                200
        );
        when(repository.existsByEventId(eventId)).thenReturn(false);

        usageRecordedService.persist(event);

        ArgumentCaptor<UsageRecordedLogEntity> captor = ArgumentCaptor.forClass(UsageRecordedLogEntity.class);
        verify(repository).save(captor.capture());
        // GOOGLE/ANTHROPIC = max(total - prompt - completion, 0)
        assertThat(captor.getValue().getEstimatedReasoningTokens()).isEqualTo(20L);
    }

    @Test
    void unknownLiteralModel_isStoredAsProviderPrefixedUnknown() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-01-01T00:00:00Z"),
                "corr-1",
                "user-1",
                null,
                null,
                "key-1",
                "deadbeef00112233",
                "managed",
                AiProvider.OPENAI,
                "unknown",
                null,
                BigDecimal.ZERO,
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                false,
                401
        );
        when(repository.existsByEventId(eventId)).thenReturn(false);
        usageRecordedService.persist(event);
        ArgumentCaptor<UsageRecordedLogEntity> captor = ArgumentCaptor.forClass(UsageRecordedLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("openai_unknown");
    }

    @Test
    void blankModelWithTokenUsageFallsBackToProviderPrefixedUnknown() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-01-01T00:00:00Z"),
                "corr-2",
                "user-1",
                null,
                null,
                "key-1",
                "deadbeef00112233",
                "managed",
                AiProvider.GOOGLE,
                null,
                new TokenUsage(null, null, null, null, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/google/v1beta/models/gemini:generateContent",
                "generativelanguage.googleapis.com",
                false,
                false,
                500
        );
        when(repository.existsByEventId(eventId)).thenReturn(false);
        usageRecordedService.persist(event);
        ArgumentCaptor<UsageRecordedLogEntity> captor = ArgumentCaptor.forClass(UsageRecordedLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("google_unknown");
    }
}
