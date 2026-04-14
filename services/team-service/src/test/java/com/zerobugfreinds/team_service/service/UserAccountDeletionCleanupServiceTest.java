package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.repository.TeamInvitationRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountDeletionCleanupServiceTest {

	@Test
	void cleanupByUserId_deletesMembershipsAndInvitations() {
		TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
		TeamInvitationRepository teamInvitationRepository = mock(TeamInvitationRepository.class);
		when(teamInvitationRepository.deleteByInviteeIdOrInviterId("user@test.com", "user@test.com")).thenReturn(3L);
		when(teamMemberRepository.deleteByUserId("user@test.com")).thenReturn(2L);

		UserAccountDeletionCleanupService service =
				new UserAccountDeletionCleanupService(teamMemberRepository, teamInvitationRepository);

		UserAccountDeletionCleanupService.CleanupResult result = service.cleanupByUserId(" user@test.com ");

		assertThat(result.deletedInvitations()).isEqualTo(3L);
		assertThat(result.deletedMemberships()).isEqualTo(2L);
		verify(teamInvitationRepository).deleteByInviteeIdOrInviterId("user@test.com", "user@test.com");
		verify(teamMemberRepository).deleteByUserId("user@test.com");
	}

	@Test
	void cleanupByUserId_requiresUserId() {
		TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
		TeamInvitationRepository teamInvitationRepository = mock(TeamInvitationRepository.class);
		UserAccountDeletionCleanupService service =
				new UserAccountDeletionCleanupService(teamMemberRepository, teamInvitationRepository);

		assertThatThrownBy(() -> service.cleanupByUserId(" "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("userId는 필수");
	}
}
