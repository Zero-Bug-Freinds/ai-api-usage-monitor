package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamInvitationStatus;
import com.zerobugfreinds.team_service.dto.InternalTeamInvitationDecisionRequest;
import com.zerobugfreinds.team_service.dto.TeamInvitationActionResponse;
import com.zerobugfreinds.team_service.entity.TeamEntity;
import com.zerobugfreinds.team_service.entity.TeamInvitationEntity;
import com.zerobugfreinds.team_service.exception.InvalidTeamInvitationStateException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamInvitationRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import com.zerobugfreinds.team_service.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceInvitationDecisionTest {

	@Test
	void processInvitationDecisionInternal_accept_addsMemberAndMarksAccepted() {
		TeamRepository teamRepository = mock(TeamRepository.class);
		TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
		TeamInvitationRepository teamInvitationRepository = mock(TeamInvitationRepository.class);
		TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
		TeamDomainEventPublisher teamDomainEventPublisher = mock(TeamDomainEventPublisher.class);
		IdentityUserSyncService identityUserSyncService = mock(IdentityUserSyncService.class);
		TeamService teamService = new TeamService(
				teamRepository,
				teamMemberRepository,
				teamInvitationRepository,
				teamApiKeyRepository,
				teamDomainEventPublisher,
				identityUserSyncService
		);

		TeamInvitationEntity invitation = TeamInvitationEntity.create(101L, "owner@test.com", "member@test.com");
		ReflectionTestUtils.setField(invitation, "id", 55L);
		TeamEntity team = TeamEntity.create("플랫폼 팀", "owner@test.com");
		ReflectionTestUtils.setField(team, "id", 101L);

		when(teamInvitationRepository.findByIdAndInviteeId(55L, "member@test.com")).thenReturn(Optional.of(invitation));
		when(teamRepository.findById(101L)).thenReturn(Optional.of(team));
		when(teamMemberRepository.existsByTeamIdAndUserId(101L, "member@test.com")).thenReturn(false);
		when(teamInvitationRepository.save(invitation)).thenReturn(invitation);

		TeamInvitationActionResponse response = teamService.processInvitationDecisionInternal(
				"member@test.com",
				55L,
				InternalTeamInvitationDecisionRequest.Decision.ACCEPT
		);

		assertThat(response.invitationId()).isEqualTo("55");
		assertThat(response.teamId()).isEqualTo("101");
		assertThat(response.status()).isEqualTo(TeamInvitationStatus.ACCEPTED.name());
		verify(teamMemberRepository).save(any());
		verify(teamInvitationRepository).save(invitation);
	}

	@Test
	void processInvitationDecisionInternal_reject_doesNotAddMemberAndMarksRejected() {
		TeamRepository teamRepository = mock(TeamRepository.class);
		TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
		TeamInvitationRepository teamInvitationRepository = mock(TeamInvitationRepository.class);
		TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
		TeamDomainEventPublisher teamDomainEventPublisher = mock(TeamDomainEventPublisher.class);
		IdentityUserSyncService identityUserSyncService = mock(IdentityUserSyncService.class);
		TeamService teamService = new TeamService(
				teamRepository,
				teamMemberRepository,
				teamInvitationRepository,
				teamApiKeyRepository,
				teamDomainEventPublisher,
				identityUserSyncService
		);

		TeamInvitationEntity invitation = TeamInvitationEntity.create(102L, "owner@test.com", "member@test.com");
		ReflectionTestUtils.setField(invitation, "id", 77L);
		TeamEntity team = TeamEntity.create("데이터 팀", "owner@test.com");
		ReflectionTestUtils.setField(team, "id", 102L);

		when(teamInvitationRepository.findByIdAndInviteeId(77L, "member@test.com")).thenReturn(Optional.of(invitation));
		when(teamRepository.findById(102L)).thenReturn(Optional.of(team));
		when(teamInvitationRepository.save(invitation)).thenReturn(invitation);

		TeamInvitationActionResponse response = teamService.processInvitationDecisionInternal(
				"member@test.com",
				77L,
				InternalTeamInvitationDecisionRequest.Decision.REJECT
		);

		assertThat(response.status()).isEqualTo(TeamInvitationStatus.REJECTED.name());
		verify(teamMemberRepository, never()).save(any());
		verify(teamInvitationRepository).save(invitation);
	}

	@Test
	void processInvitationDecisionInternal_acceptAlreadyProcessedInvitation_throwsBadRequestException() {
		TeamRepository teamRepository = mock(TeamRepository.class);
		TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
		TeamInvitationRepository teamInvitationRepository = mock(TeamInvitationRepository.class);
		TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
		TeamDomainEventPublisher teamDomainEventPublisher = mock(TeamDomainEventPublisher.class);
		IdentityUserSyncService identityUserSyncService = mock(IdentityUserSyncService.class);
		TeamService teamService = new TeamService(
				teamRepository,
				teamMemberRepository,
				teamInvitationRepository,
				teamApiKeyRepository,
				teamDomainEventPublisher,
				identityUserSyncService
		);

		TeamInvitationEntity invitation = TeamInvitationEntity.create(103L, "owner@test.com", "member@test.com");
		ReflectionTestUtils.setField(invitation, "id", 88L);
		invitation.reject();
		when(teamInvitationRepository.findByIdAndInviteeId(88L, "member@test.com")).thenReturn(Optional.of(invitation));

		assertThatThrownBy(() -> teamService.processInvitationDecisionInternal(
				"member@test.com",
				88L,
				InternalTeamInvitationDecisionRequest.Decision.ACCEPT
		))
				.isInstanceOf(InvalidTeamInvitationStateException.class)
				.hasMessageContaining("이미 처리된 초대");
	}
}
