package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamMemberRole;
import com.zerobugfreinds.team_service.dto.TeamSummaryResponse;
import com.zerobugfreinds.team_service.dto.InternalTeamDetailResponse;
import com.zerobugfreinds.team_service.entity.TeamEntity;
import com.zerobugfreinds.team_service.entity.TeamMemberEntity;
import com.zerobugfreinds.team_service.event.TeamMemberAddedEvent;
import com.zerobugfreinds.team_service.exception.DuplicateTeamMemberException;
import com.zerobugfreinds.team_service.exception.ForbiddenTeamAccessException;
import com.zerobugfreinds.team_service.exception.TeamNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import com.zerobugfreinds.team_service.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeamService {
	private final TeamRepository teamRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final IdentityUserLookupClient identityUserLookupClient;
	private final TeamMemberAddedEventPublisher teamMemberAddedEventPublisher;

	public TeamService(
			TeamRepository teamRepository,
			TeamMemberRepository teamMemberRepository,
			IdentityUserLookupClient identityUserLookupClient,
			TeamMemberAddedEventPublisher teamMemberAddedEventPublisher
	) {
		this.teamRepository = teamRepository;
		this.teamMemberRepository = teamMemberRepository;
		this.identityUserLookupClient = identityUserLookupClient;
		this.teamMemberAddedEventPublisher = teamMemberAddedEventPublisher;
	}

	@Transactional
	public TeamSummaryResponse createTeam(String actorUserId, String name) {
		if (!StringUtils.hasText(actorUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		String trimmed = StringUtils.hasText(name) ? name.trim() : "";
		if (!StringUtils.hasText(trimmed)) {
			throw new IllegalArgumentException("팀 이름은 필수입니다");
		}
		TeamEntity saved = teamRepository.save(TeamEntity.create(trimmed, actorUserId));
		teamMemberRepository.save(TeamMemberEntity.of(saved.getId(), actorUserId, TeamMemberRole.OWNER));
		return new TeamSummaryResponse(String.valueOf(saved.getId()), saved.getName());
	}

	@Transactional(readOnly = true)
	public List<TeamSummaryResponse> getMyTeams(String actorUserId) {
		if (!StringUtils.hasText(actorUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		List<TeamMemberEntity> memberships = teamMemberRepository.findAllByUserId(actorUserId);
		if (memberships.isEmpty()) {
			return List.of();
		}
		List<Long> teamIds = memberships.stream().map(TeamMemberEntity::getTeamId).toList();
		Map<Long, TeamEntity> teamById = teamRepository.findAllById(teamIds).stream()
				.collect(Collectors.toMap(TeamEntity::getId, t -> t));

		List<TeamSummaryResponse> result = new ArrayList<>();
		for (Long teamId : teamIds) {
			TeamEntity team = teamById.get(teamId);
			if (team != null) {
				result.add(new TeamSummaryResponse(String.valueOf(team.getId()), team.getName()));
			}
		}
		return result;
	}

	@Transactional
	public TeamSummaryResponse inviteMember(String actorUserId, Long teamId, String inviteeUserId) {
		if (!StringUtils.hasText(actorUserId) || !StringUtils.hasText(inviteeUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		String invitee = inviteeUserId.trim();
		if (!identityUserLookupClient.existsByEmail(invitee)) {
			throw new IllegalArgumentException("존재하지 않는 사용자 아이디(이메일)입니다");
		}
		TeamEntity team = teamRepository.findById(teamId)
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));

		if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, actorUserId)) {
			throw new ForbiddenTeamAccessException("팀 멤버만 초대할 수 있습니다");
		}
		if (teamMemberRepository.existsByTeamIdAndUserId(teamId, invitee)) {
			throw new DuplicateTeamMemberException("이미 팀에 참여 중인 사용자입니다");
		}
		teamMemberRepository.save(TeamMemberEntity.of(teamId, invitee, TeamMemberRole.MEMBER));
		teamMemberAddedEventPublisher.publish(
				new TeamMemberAddedEvent(
						invitee,
						actorUserId.trim(),
						String.valueOf(team.getId()),
						team.getName(),
						Instant.now()
				)
		);
		return new TeamSummaryResponse(String.valueOf(team.getId()), team.getName());
	}

	@Transactional(readOnly = true)
	public InternalTeamDetailResponse getTeamDetailInternal(Long teamId) {
		TeamEntity team = teamRepository.findById(teamId)
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));
		return new InternalTeamDetailResponse(
				String.valueOf(team.getId()),
				team.getName(),
				team.getCreatedBy(),
				team.getCreatedAt()
		);
	}

	@Transactional(readOnly = true)
	public List<String> getTeamMemberUserIds(String actorUserId, Long teamId) {
		if (!StringUtils.hasText(actorUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, actorUserId)) {
			throw new ForbiddenTeamAccessException("팀 멤버만 조회할 수 있습니다");
		}
		return teamMemberRepository.findAllByTeamId(teamId).stream()
				.map(TeamMemberEntity::getUserId)
				.filter(StringUtils::hasText)
				.sorted(Comparator.naturalOrder())
				.toList();
	}
}
