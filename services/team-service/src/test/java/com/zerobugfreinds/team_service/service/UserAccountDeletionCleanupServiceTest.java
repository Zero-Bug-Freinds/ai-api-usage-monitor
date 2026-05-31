package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import com.zerobugfreinds.team_service.domain.TeamMemberRole;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.entity.TeamMemberEntity;
import com.zerobugfreinds.team_service.repository.IdentityUserSyncRepository;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamInvitationRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountDeletionCleanupServiceTest {

	@Mock
	private TeamMemberRepository teamMemberRepository;
	@Mock
	private TeamInvitationRepository teamInvitationRepository;
	@Mock
	private TeamApiKeyRepository teamApiKeyRepository;
	@Mock
	private IdentityUserSyncRepository identityUserSyncRepository;
	@Mock
	private IdentityUserSyncService identityUserSyncService;
	@Mock
	private TeamService teamService;
	@Mock
	private TeamApiKeyService teamApiKeyService;

	@InjectMocks
	private UserAccountDeletionCleanupService service;

	@Test
	void cleanup_ownerDeletesTeamAndApiKeys() {
		UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(1L, "owner@test.com");
		when(identityUserSyncService.resolveMembershipLookupCandidates("owner@test.com"))
				.thenReturn(Set.of("owner@test.com"));
		when(identityUserSyncService.resolveMembershipLookupCandidates("1")).thenReturn(Set.of("owner@test.com"));

		TeamMemberEntity ownerMembership = mock(TeamMemberEntity.class);
		when(ownerMembership.getTeamId()).thenReturn(10L);
		when(teamMemberRepository.findAllByUserIdInAndRole(List.of("owner@test.com"), TeamMemberRole.OWNER))
				.thenReturn(List.of(ownerMembership));
		when(teamMemberRepository.findAllByUserIdInAndRole(List.of("1"), TeamMemberRole.OWNER))
				.thenReturn(List.of());

		TeamApiKeyEntity apiKey = mock(TeamApiKeyEntity.class);
		when(apiKey.getId()).thenReturn(99L);
		when(teamApiKeyRepository.findAllByTeamIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(apiKey));
		when(teamInvitationRepository.deleteByInviteeIdOrInviterId("owner@test.com", "owner@test.com")).thenReturn(1L);
		when(teamInvitationRepository.deleteByInviteeIdOrInviterId("1", "1")).thenReturn(0L);
		when(teamMemberRepository.findAllByUserIdIn(List.of("owner@test.com"))).thenReturn(List.of(ownerMembership));
		when(teamMemberRepository.findAllByUserIdIn(List.of("1"))).thenReturn(List.of());
		when(teamMemberRepository.deleteByUserId("owner@test.com")).thenReturn(1L);
		when(teamMemberRepository.deleteByUserId("1")).thenReturn(0L);
		when(identityUserSyncRepository.existsById("owner@test.com")).thenReturn(true);

		UserAccountDeletionCleanupService.CleanupResult result = service.cleanup(event);

		verify(teamApiKeyService).delete("owner@test.com", 10L, 99L, 0, false);
		verify(teamService).deleteTeamForAccountDeletion("owner@test.com", 10L);
		verify(teamService, never()).removeMemberForAccountDeletion(eq("owner@test.com"), eq(10L));
		assertThat(result.deletedTeams()).isEqualTo(1);
		assertThat(result.deletedTeamApiKeys()).isEqualTo(1);
	}

	@Test
	void cleanup_memberRemovesMembershipOnly() {
		UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(2L, "member@test.com");
		when(identityUserSyncService.resolveMembershipLookupCandidates("member@test.com"))
				.thenReturn(Set.of("member@test.com"));
		when(identityUserSyncService.resolveMembershipLookupCandidates("2")).thenReturn(Set.of("member@test.com"));

		when(teamMemberRepository.findAllByUserIdInAndRole(List.of("member@test.com"), TeamMemberRole.OWNER))
				.thenReturn(List.of());
		when(teamMemberRepository.findAllByUserIdInAndRole(List.of("2"), TeamMemberRole.OWNER))
				.thenReturn(List.of());

		TeamMemberEntity memberMembership = mock(TeamMemberEntity.class);
		when(memberMembership.getTeamId()).thenReturn(20L);
		when(memberMembership.getRole()).thenReturn(TeamMemberRole.MEMBER);
		when(teamMemberRepository.findAllByUserIdIn(List.of("member@test.com"))).thenReturn(List.of(memberMembership));
		when(teamMemberRepository.findAllByUserIdIn(List.of("2"))).thenReturn(List.of());
		when(teamInvitationRepository.deleteByInviteeIdOrInviterId("member@test.com", "member@test.com")).thenReturn(0L);
		when(teamInvitationRepository.deleteByInviteeIdOrInviterId("2", "2")).thenReturn(0L);
		when(teamMemberRepository.deleteByUserId("member@test.com")).thenReturn(1L);
		when(teamMemberRepository.deleteByUserId("2")).thenReturn(0L);

		UserAccountDeletionCleanupService.CleanupResult result = service.cleanup(event);

		verify(teamService).removeMemberForAccountDeletion("member@test.com", 20L);
		verify(teamService, never()).deleteTeamForAccountDeletion(eq("member@test.com"), eq(20L));
		verify(teamApiKeyService, never()).delete(eq("member@test.com"), eq(20L), eq(99L), eq(0), eq(false));
		assertThat(result.deletedTeams()).isZero();
		assertThat(result.removedMemberMemberships()).isEqualTo(1);
	}

	@Test
	void cleanup_requiresEvent() {
		assertThatThrownBy(() -> service.cleanup(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("event is required");
	}
}
