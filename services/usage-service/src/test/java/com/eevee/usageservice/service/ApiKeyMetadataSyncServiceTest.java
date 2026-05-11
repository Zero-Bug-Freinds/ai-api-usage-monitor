package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.mq.TeamApiKeyDeletedEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionCancelledEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionScheduledEvent;
import com.eevee.usageservice.mq.TeamApiKeyRegisteredEvent;
import com.eevee.usageservice.mq.TeamApiKeyStatus;
import com.eevee.usageservice.mq.TeamApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.TeamApiKeyUpdatedEvent;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyMetadataSyncServiceTest {

    @Mock
    private ApiKeyMetadataRepository apiKeyMetadataRepository;
    @Mock
    private UsageRecordedLogRepository usageRecordedLogRepository;

    private ApiKeyMetadataSyncService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyMetadataSyncService(apiKeyMetadataRepository, usageRecordedLogRepository);
    }

    @Test
    void upsertFromTeamRegistered_createsActiveMetadata() {
        TeamApiKeyRegisteredEvent event = new TeamApiKeyRegisteredEvent(
                "TEAM_API_KEY_REGISTERED",
                Instant.parse("2026-05-01T00:00:00Z"),
                101L,
                1L,
                "OPENAI",
                "team-main",
                "owner-1"
        );
        when(apiKeyMetadataRepository.findById("101")).thenReturn(Optional.empty());

        service.upsertFromTeamRegistered(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getKeyId()).isEqualTo("101");
        assertThat(captor.getValue().getUserId()).isEqualTo("owner-1");
        assertThat(captor.getValue().getProvider()).isEqualTo("OPENAI");
        assertThat(captor.getValue().getAlias()).isEqualTo("team-main");
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    void handleTeamDeletionScheduled_setsDeletionRequested() {
        TeamApiKeyDeletionScheduledEvent event = new TeamApiKeyDeletionScheduledEvent(
                "TEAM_API_KEY_DELETION_SCHEDULED",
                Instant.parse("2026-05-01T00:00:00Z"),
                202L,
                2L,
                "GOOGLE",
                "team-google",
                "owner-2"
        );
        when(apiKeyMetadataRepository.findById("202")).thenReturn(Optional.empty());

        service.handleTeamDeletionScheduled(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiKeyStatus.DELETION_REQUESTED);
    }

    @Test
    void handleTeamDeletionCancelled_setsActive() {
        TeamApiKeyDeletionCancelledEvent event = new TeamApiKeyDeletionCancelledEvent(
                "TEAM_API_KEY_DELETION_CANCELLED",
                Instant.parse("2026-05-01T00:00:00Z"),
                303L,
                3L,
                "OPENAI",
                "team-openai",
                "owner-3"
        );
        when(apiKeyMetadataRepository.findById("303")).thenReturn(Optional.empty());

        service.handleTeamDeletionCancelled(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    void handleTeamDeleted_setsDeleted() {
        TeamApiKeyDeletedEvent event = new TeamApiKeyDeletedEvent(
                "TEAM_API_KEY_DELETED",
                Instant.parse("2026-05-01T00:00:00Z"),
                404L,
                4L,
                "OPENAI",
                "team-delete",
                "owner-4"
        );
        when(apiKeyMetadataRepository.findById("404")).thenReturn(Optional.empty());

        service.handleTeamDeleted(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiKeyStatus.DELETED);
    }

    @Test
    void upsertFromTeamStatusChanged_mapsStatusFromEvent() {
        TeamApiKeyStatusChangedEvent event = new TeamApiKeyStatusChangedEvent(
                "TEAM_API_KEY_STATUS_CHANGED",
                Instant.parse("2026-05-01T00:00:00Z"),
                505L,
                5L,
                "owner-5",
                "OPENAI",
                "team-status",
                TeamApiKeyStatus.DELETION_REQUESTED
        );
        when(apiKeyMetadataRepository.findById("505")).thenReturn(Optional.empty());

        service.upsertFromTeamStatusChanged(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiKeyStatus.DELETION_REQUESTED);
    }

    @Test
    void upsertFromTeamStatusChanged_usesExistingUserIdWhenOwnerMissing() {
        ApiKeyMetadataEntity existing = ApiKeyMetadataEntity.create("606", "owner-existing");
        existing.apply("owner-existing", "6", "OPENAI", "old", ApiKeyStatus.ACTIVE, Instant.parse("2026-04-01T00:00:00Z"));
        when(apiKeyMetadataRepository.findById("606")).thenReturn(Optional.of(existing));

        TeamApiKeyStatusChangedEvent event = new TeamApiKeyStatusChangedEvent(
                "TEAM_API_KEY_STATUS_CHANGED",
                Instant.parse("2026-05-01T00:00:00Z"),
                606L,
                6L,
                null,
                "OPENAI",
                "new-alias",
                TeamApiKeyStatus.ACTIVE
        );

        service.upsertFromTeamStatusChanged(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("owner-existing");
        assertThat(captor.getValue().getAlias()).isEqualTo("new-alias");
    }

    @Test
    void upsertFromTeamStatusChanged_skipsWhenOwnerMissingAndNoExistingEntity() {
        when(apiKeyMetadataRepository.findById("707")).thenReturn(Optional.empty());
        TeamApiKeyStatusChangedEvent event = new TeamApiKeyStatusChangedEvent(
                "TEAM_API_KEY_STATUS_CHANGED",
                Instant.parse("2026-05-01T00:00:00Z"),
                707L,
                7L,
                null,
                "OPENAI",
                "alias",
                TeamApiKeyStatus.ACTIVE
        );

        service.upsertFromTeamStatusChanged(event);

        verify(apiKeyMetadataRepository, never()).save(org.mockito.ArgumentMatchers.any(ApiKeyMetadataEntity.class));
    }

    @Test
    void upsertFromUsageRecordedEvent_doesNotOverwriteProviderFromCall() {
        ApiKeyMetadataEntity existing = ApiKeyMetadataEntity.create("101", "user-a");
        existing.apply("user-a", null, "OPENAI", "cafe-1", ApiKeyStatus.ACTIVE, Instant.parse("2026-04-01T00:00:00Z"));
        when(apiKeyMetadataRepository.findById("101")).thenReturn(Optional.of(existing));

        UsageRecordedEvent event = new UsageRecordedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-05-11T12:00:00Z"),
                "c-1",
                "user-a",
                null,
                null,
                "101",
                "cafe-1",
                null,
                "fp",
                "team",
                AiProvider.GOOGLE,
                "gemini-pro",
                new TokenUsage("gemini-pro", 1L, 1L, 2L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/p",
                "g.example",
                false,
                true,
                200
        );

        service.upsertFromUsageRecordedEvent(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("OPENAI");
    }

    @Test
    void upsertFromUsageRecordedEvent_prefersTeamApiKeyIdAsMetadataKey() {
        when(apiKeyMetadataRepository.findById("999")).thenReturn(Optional.empty());

        UsageRecordedEvent event = new UsageRecordedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-05-11T12:00:00Z"),
                "c-1",
                "user@example.com",
                null,
                "1",
                "888",
                "team-alias",
                "999",
                "fp",
                "team",
                AiProvider.OPENAI,
                "gpt-4o",
                new TokenUsage("gpt-4o", 1L, 1L, 2L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/p",
                "api.openai.com",
                false,
                true,
                200
        );

        service.upsertFromUsageRecordedEvent(event);

        verify(apiKeyMetadataRepository).findById(eq("999"));
        verify(apiKeyMetadataRepository, never()).findById(eq("888"));
        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getKeyId()).isEqualTo("999");
        assertThat(captor.getValue().getTeamId()).isEqualTo("1");
    }

    @Test
    void upsertFromTeamRegistered_skipsSaveWhenProviderMissingAndNoExistingMetadata() {
        TeamApiKeyRegisteredEvent event = new TeamApiKeyRegisteredEvent(
                "TEAM_API_KEY_REGISTERED",
                Instant.parse("2026-05-01T00:00:00Z"),
                888L,
                1L,
                null,
                "alias-only",
                "owner-1"
        );
        when(apiKeyMetadataRepository.findById("888")).thenReturn(Optional.empty());

        service.upsertFromTeamRegistered(event);

        verify(apiKeyMetadataRepository, never()).save(org.mockito.ArgumentMatchers.any(ApiKeyMetadataEntity.class));
    }

    @Test
    void upsertFromTeamUpdated_keepsExistingProviderWhenEventProviderBlank() {
        ApiKeyMetadataEntity existing = ApiKeyMetadataEntity.create("808", "owner-8");
        existing.apply("owner-8", "8", "OPENAI", "old-alias", ApiKeyStatus.ACTIVE, Instant.parse("2026-04-01T00:00:00Z"));
        when(apiKeyMetadataRepository.findById("808")).thenReturn(Optional.of(existing));

        TeamApiKeyUpdatedEvent event = new TeamApiKeyUpdatedEvent(
                "TEAM_API_KEY_UPDATED",
                Instant.parse("2026-05-01T00:00:00Z"),
                808L,
                8L,
                null,
                "new-alias",
                "owner-8"
        );

        service.upsertFromTeamUpdated(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("OPENAI");
        assertThat(captor.getValue().getAlias()).isEqualTo("new-alias");
    }
}
