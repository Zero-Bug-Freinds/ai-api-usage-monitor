package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.dto.TeamResponse;
import com.zerobugfreinds.team_service.entity.TeamEntity;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamInvitationRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberCountRow;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import com.zerobugfreinds.team_service.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceGetTeamsTest {

	private record CountRow(Long teamId, Long memberCount) implements TeamMemberCountRow {
		@Override
		public Long getTeamId() {
			return teamId;
		}

		@Override
		public Long getMemberCount() {
			return memberCount;
		}
	}

	@Test
	void getTeams_batchesMemberCountsPerPage() {
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

		TeamEntity t1 = TeamEntity.create("Alpha", "owner");
		ReflectionTestUtils.setField(t1, "id", 10L);
		TeamEntity t2 = TeamEntity.create("Beta", "owner");
		ReflectionTestUtils.setField(t2, "id", 20L);
		Pageable pageable = PageRequest.of(0, 20);
		Page<TeamEntity> entityPage = new PageImpl<>(List.of(t1, t2), pageable, 2);
		when(teamRepository.findAll(pageable)).thenReturn(entityPage);
		when(teamMemberRepository.countGroupedByTeamIdIn(List.of(10L, 20L)))
				.thenReturn(List.of(new CountRow(10L, 2L), new CountRow(20L, 7L)));

		Page<TeamResponse> result = teamService.getTeams(null, pageable);

		assertThat(result.getContent()).hasSize(2);
		assertThat(result.getContent().get(0).memberCount()).isEqualTo(2L);
		assertThat(result.getContent().get(1).memberCount()).isEqualTo(7L);
		verify(teamMemberRepository).countGroupedByTeamIdIn(List.of(10L, 20L));
		verify(teamMemberRepository, never()).countByTeamId(anyLong());
	}

	@Test
	void getTeams_emptyPage_skipsBatchCountQuery() {
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

		Pageable pageable = PageRequest.of(0, 20);
		when(teamRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

		Page<TeamResponse> result = teamService.getTeams(null, pageable);

		assertThat(result.getContent()).isEmpty();
		verify(teamMemberRepository, never()).countGroupedByTeamIdIn(anyList());
	}
}
