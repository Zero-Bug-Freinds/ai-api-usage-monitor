package com.eevee.usageservice.service;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.mq.TeamApiKeyDeletedEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionCancelledEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionScheduledEvent;
import com.eevee.usageservice.mq.TeamApiKeyRegisteredEvent;
import com.eevee.usageservice.mq.TeamApiKeyStatus;
import com.eevee.usageservice.mq.TeamApiKeyStatusChangedEvent;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        existing.apply("owner-existing", "OPENAI", "old", ApiKeyStatus.ACTIVE, Instant.parse("2026-04-01T00:00:00Z"));
        when(apiKeyMetadataRepository.findById("606")).thenReturn(Optional.of(existing));

        TeamApiKeyStatusChangedEvent event = new TeamApiKeyStatusChangedEvent(
                "TEAM_API_KEY_STATUS_CHANGED",
                Instant.parse("2026-05-01T00:00:00Z"),
                606L,
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
                null,
                "OPENAI",
                "alias",
                TeamApiKeyStatus.ACTIVE
        );

        service.upsertFromTeamStatusChanged(event);

        verify(apiKeyMetadataRepository, never()).save(org.mockito.ArgumentMatchers.any(ApiKeyMetadataEntity.class));
    }
}
