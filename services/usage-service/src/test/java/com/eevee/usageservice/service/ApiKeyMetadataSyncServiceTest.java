package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyMetadataEntityId;
import com.eevee.usageservice.domain.ApiKeyMetadataScope;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.mq.TeamApiKeyDeletedEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionCancelledEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionScheduledEvent;
import com.eevee.usageservice.mq.ExternalApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.TeamApiKeyRegisteredEvent;
import com.eevee.usageservice.mq.TeamApiKeyStatus;
import com.eevee.usageservice.mq.TeamApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.TeamApiKeyUpdatedEvent;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.eevee.usageservice.service.bff.team.TeamServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyMetadataSyncServiceTest {

    @Mock
    private ApiKeyMetadataRepository apiKeyMetadataRepository;
    @Mock
    private UsageRecordedLogRepository usageRecordedLogRepository;
    @Mock
    private TeamServiceClient teamServiceClient;

    private ApiKeyMetadataSyncService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyMetadataSyncService(apiKeyMetadataRepository, usageRecordedLogRepository, teamServiceClient);
    }

    private void stubTeamMembers(String actorUserId, String teamId, List<String> memberUserIds) {
        when(teamServiceClient.fetchTeamMemberUserIds(eq(actorUserId), eq(teamId))).thenReturn(memberUserIds);
    }

    @Test
    void upsertFromTeamRegistered_createsActiveMetadataForEachMember() {
        TeamApiKeyRegisteredEvent event = new TeamApiKeyRegisteredEvent(
                "TEAM_API_KEY_REGISTERED",
                Instant.parse("2026-05-01T00:00:00Z"),
                101L,
                1L,
                "OPENAI",
                "team-main",
                "owner-1",
                null,
                null
        );
        stubTeamMembers("owner-1", "1", List.of("owner-1"));
        var id = ApiKeyMetadataEntityId.team("101", "owner-1");
        when(apiKeyMetadataRepository.findById(id)).thenReturn(Optional.empty());

        service.upsertFromTeamRegistered(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getKeyId()).isEqualTo("101");
        assertThat(captor.getValue().getUserId()).isEqualTo("owner-1");
        assertThat(captor.getValue().getTeamId()).isEqualTo("1");
        assertThat(captor.getValue().getProvider()).isEqualTo("OPENAI");
        assertThat(captor.getValue().getAlias()).isEqualTo("team-main");
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        verify(apiKeyMetadataRepository).deleteTeamMetadataRowsForKeyNotInMemberList("101", "1", List.of("owner-1"));
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
                "owner-2",
                null,
                null
        );
        stubTeamMembers("owner-2", "2", List.of("owner-2"));
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("202", "owner-2"))).thenReturn(Optional.empty());

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
                "owner-3",
                null,
                null
        );
        stubTeamMembers("owner-3", "3", List.of("owner-3"));
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("303", "owner-3"))).thenReturn(Optional.empty());

        service.handleTeamDeletionCancelled(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    void handleTeamDeleted_removesAllTeamRows() {
        TeamApiKeyDeletedEvent event = new TeamApiKeyDeletedEvent(
                "TEAM_API_KEY_DELETED",
                Instant.parse("2026-05-01T00:00:00Z"),
                404L,
                4L,
                "OPENAI",
                "team-delete",
                "owner-4"
        );

        service.handleTeamDeleted(event);

        verify(apiKeyMetadataRepository).deleteAllTeamMetadataRowsForKey("404", "4");
        verify(apiKeyMetadataRepository, never()).save(any(ApiKeyMetadataEntity.class));
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
        stubTeamMembers("owner-5", "5", List.of("owner-5"));
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("505", "owner-5"))).thenReturn(Optional.empty());

        service.upsertFromTeamStatusChanged(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiKeyStatus.DELETION_REQUESTED);
    }

    @Test
    void upsertFromTeamStatusChanged_fanOutsToAllMembers() {
        stubTeamMembers("owner-existing", "6", List.of("owner-existing", "member-b"));
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("606", "owner-existing"))).thenReturn(Optional.empty());
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("606", "member-b"))).thenReturn(Optional.empty());

        TeamApiKeyStatusChangedEvent event = new TeamApiKeyStatusChangedEvent(
                "TEAM_API_KEY_STATUS_CHANGED",
                Instant.parse("2026-05-01T00:00:00Z"),
                606L,
                6L,
                "owner-existing",
                "OPENAI",
                "new-alias",
                TeamApiKeyStatus.ACTIVE
        );

        service.upsertFromTeamStatusChanged(event);

        verify(apiKeyMetadataRepository, times(2)).save(any(ApiKeyMetadataEntity.class));
        verify(apiKeyMetadataRepository).deleteTeamMetadataRowsForKeyNotInMemberList(
                "606",
                "6",
                List.of("owner-existing", "member-b")
        );
    }

    @Test
    void upsertFromTeamStatusChanged_skipsWhenOwnerMissing() {
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

        verify(apiKeyMetadataRepository, never()).save(any(ApiKeyMetadataEntity.class));
        verify(teamServiceClient, never()).fetchTeamMemberUserIds(any(), any());
    }

    @Test
    void upsertFromUsageRecordedEvent_doesNotOverwriteProviderFromCall() {
        ApiKeyMetadataEntity existing = ApiKeyMetadataEntity.createPersonal("101", "user-a");
        existing.apply(null, "OPENAI", "cafe-1", ApiKeyStatus.ACTIVE, Instant.parse("2026-04-01T00:00:00Z"));
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.personal("101", "user-a"))).thenReturn(Optional.of(existing));

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
                "managed",
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
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("999", "user@example.com"))).thenReturn(Optional.empty());

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

        verify(apiKeyMetadataRepository).findById(eq(ApiKeyMetadataEntityId.team("999", "user@example.com")));
        verify(apiKeyMetadataRepository, never()).findById(eq(ApiKeyMetadataEntityId.personal("888", "user@example.com")));
        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getKeyId()).isEqualTo("999");
        assertThat(captor.getValue().getTeamId()).isEqualTo("1");
    }

    @Test
    void upsertFromUsageRecordedEvent_managedSourceUsesPersonalMetadataDespiteTeamFields() {
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.personal("888", "user@example.com")))
                .thenReturn(Optional.empty());

        UsageRecordedEvent event = new UsageRecordedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-05-11T12:00:00Z"),
                "c-1",
                "user@example.com",
                null,
                "1",
                "888",
                "alias",
                "999",
                "fp",
                "managed",
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

        verify(apiKeyMetadataRepository).findById(eq(ApiKeyMetadataEntityId.personal("888", "user@example.com")));
        verify(apiKeyMetadataRepository, never()).findById(eq(ApiKeyMetadataEntityId.team("999", "user@example.com")));
    }

    @Test
    void upsertFromIdentity_setsTeamIdNullOnPersonalMetadata() {
        ExternalApiKeyStatusChangedEvent event = new ExternalApiKeyStatusChangedEvent(
                1,
                Instant.parse("2026-05-01T00:00:00Z"),
                42L,
                "alias",
                7L,
                "OPENAI",
                ExternalApiKeyStatus.ACTIVE
        );
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.personal("42", "7"))).thenReturn(Optional.empty());

        service.upsertFromIdentity(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getTeamId()).isNull();
        assertThat(captor.getValue().getKeyScope()).isEqualTo(ApiKeyMetadataScope.PERSONAL);
    }

    @Test
    void upsertFromTeamRegistered_fanOutFromRecipientUserIdsWithoutCallingTeamService() {
        TeamApiKeyRegisteredEvent event = new TeamApiKeyRegisteredEvent(
                "TEAM_API_KEY_REGISTERED",
                Instant.parse("2026-05-01T00:00:00Z"),
                101L,
                1L,
                "OPENAI",
                "team-main",
                "owner-1",
                null,
                List.of("user-a", "user-b", "user-c")
        );
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("101", "user-a"))).thenReturn(Optional.empty());
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("101", "user-b"))).thenReturn(Optional.empty());
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("101", "user-c"))).thenReturn(Optional.empty());

        service.upsertFromTeamRegistered(event);

        verify(teamServiceClient, never()).fetchTeamMemberUserIds(any(), any());
        verify(apiKeyMetadataRepository, times(3)).save(any(ApiKeyMetadataEntity.class));
        verify(apiKeyMetadataRepository).deleteTeamMetadataRowsForKeyNotInMemberList(
                "101",
                "1",
                List.of("user-a", "user-b", "user-c")
        );
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
                "owner-1",
                null,
                null
        );
        stubTeamMembers("owner-1", "1", List.of("owner-1"));
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("888", "owner-1"))).thenReturn(Optional.empty());

        service.upsertFromTeamRegistered(event);

        verify(apiKeyMetadataRepository, never()).save(any(ApiKeyMetadataEntity.class));
    }

    @Test
    void upsertFromTeamUpdated_keepsExistingProviderWhenEventProviderBlank() {
        ApiKeyMetadataEntity existing = ApiKeyMetadataEntity.createTeamRow("808", "owner-8", "8");
        existing.apply("8", "OPENAI", "old-alias", ApiKeyStatus.ACTIVE, Instant.parse("2026-04-01T00:00:00Z"));
        stubTeamMembers("owner-8", "8", List.of("owner-8"));
        when(apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team("808", "owner-8"))).thenReturn(Optional.of(existing));

        TeamApiKeyUpdatedEvent event = new TeamApiKeyUpdatedEvent(
                "TEAM_API_KEY_UPDATED",
                Instant.parse("2026-05-01T00:00:00Z"),
                808L,
                8L,
                null,
                "new-alias",
                "owner-8",
                null,
                null
        );

        service.upsertFromTeamUpdated(event);

        ArgumentCaptor<ApiKeyMetadataEntity> captor = ArgumentCaptor.forClass(ApiKeyMetadataEntity.class);
        verify(apiKeyMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("OPENAI");
        assertThat(captor.getValue().getAlias()).isEqualTo("new-alias");
    }
}
