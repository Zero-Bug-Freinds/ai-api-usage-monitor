package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private UsageRecordedService usageRecordedService;

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
                new TokenUsage("gpt-4o-mini", 10L, 20L, 30L),
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
                new TokenUsage("gpt-4o-mini", 10L, 20L, 30L),
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
}
