package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamMemberRole;
import com.zerobugfreinds.team_service.domain.TeamInvitationStatus;
import com.zerobugfreinds.team_service.dto.InternalTeamDetailResponse;
import com.zerobugfreinds.team_service.dto.TeamInvitationActionResponse;
import com.zerobugfreinds.team_service.dto.TeamInvitationResponse;
import com.zerobugfreinds.team_service.dto.TeamSummaryResponse;
import com.zerobugfreinds.team_service.dto.InternalTeamDetailResponse;
import com.zerobugfreinds.team_service.entity.TeamEntity;
import com.zerobugfreinds.team_service.entity.TeamInvitationEntity;
import com.zerobugfreinds.team_service.entity.TeamMemberEntity;
import com.zerobugfreinds.team_service.event.TeamMemberAddedEvent;
import com.zerobugfreinds.team_service.exception.DuplicateTeamMemberException;
import com.zerobugfreinds.team_service.exception.DuplicateTeamInvitationException;
import com.zerobugfreinds.team_service.exception.ForbiddenTeamAccessException;
import com.zerobugfreinds.team_service.exception.InvalidTeamInvitationStateException;
import com.zerobugfreinds.team_service.exception.OwnerPermissionRequiredException;
import com.zerobugfreinds.team_service.exception.TeamDeletionBlockedException;
import com.zerobugfreinds.team_service.exception.TeamNotFoundException;
import com.zerobugfreinds.team_service.exception.TeamInvitationNotFoundException;
import com.zerobugfreinds.team_service.event.TeamMemberInvitedEvent;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamInvitationRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import com.zerobugfreinds.team_service.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TeamService {
	private final TeamRepository teamRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final TeamInvitationRepository teamInvitationRepository;
	private final TeamApiKeyRepository teamApiKeyRepository;
	private final IdentityUserLookupClient identityUserLookupClient;
	private final TeamInvitationEventPublisher teamInvitationEventPublisher;
	private final TeamMemberAddedEventPublisher teamMemberAddedEventPublisher;

	public TeamService(
			TeamRepository teamRepository,
			TeamMemberRepository teamMemberRepository,
			TeamInvitationRepository teamInvitationRepository,
			TeamApiKeyRepository teamApiKeyRepository,
			IdentityUserLookupClient identityUserLookupClient,
			TeamInvitationEventPublisher teamInvitationEventPublisher,
			TeamMemberAddedEventPublisher teamMemberAddedEventPublisher
	) {
		this.teamRepository = teamRepository;
		this.teamMemberRepository = teamMemberRepository;
		this.teamInvitationRepository = teamInvitationRepository;
		this.teamApiKeyRepository = teamApiKeyRepository;
		this.identityUserLookupClient = identityUserLookupClient;
		this.teamInvitationEventPublisher = teamInvitationEventPublisher;
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
		if (teamInvitationRepository.existsByTeamIdAndInviteeIdAndStatus(teamId, invitee, TeamInvitationStatus.PENDING)) {
			throw new DuplicateTeamInvitationException("이미 대기 중인 팀 초대가 있습니다");
		}

		TeamInvitationEntity invitation = teamInvitationRepository.save(
				TeamInvitationEntity.create(teamId, actorUserId.trim(), invitee)
		);
		teamInvitationEventPublisher.publish(
				new TeamMemberInvitedEvent(
						String.valueOf(invitation.getId()),
						invitee,
						actorUserId.trim(),
						String.valueOf(team.getId()),
						team.getName(),
						invitation.getCreatedAt()
				)
		);
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

	@Transactional(readOnly = true)
	public List<TeamInvitationResponse> getMyPendingInvitations(String actorUserId) {
		if (!StringUtils.hasText(actorUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		List<TeamInvitationEntity> invitations =
				teamInvitationRepository.findAllByInviteeIdAndStatusOrderByCreatedAtDesc(actorUserId, TeamInvitationStatus.PENDING);
		if (invitations.isEmpty()) {
			return List.of();
		}

		Set<Long> teamIds = new HashSet<>();
		for (TeamInvitationEntity invitation : invitations) {
			teamIds.add(invitation.getTeamId());
		}
		Map<Long, TeamEntity> teamsById = teamRepository.findAllById(teamIds).stream()
				.collect(Collectors.toMap(TeamEntity::getId, t -> t));

		List<TeamInvitationResponse> result = new ArrayList<>();
		for (TeamInvitationEntity invitation : invitations) {
			TeamEntity team = teamsById.get(invitation.getTeamId());
			if (team == null) {
				continue;
			}
			result.add(new TeamInvitationResponse(
					String.valueOf(invitation.getId()),
					String.valueOf(invitation.getTeamId()),
					team.getName(),
					invitation.getInviterId(),
					invitation.getInviteeId(),
					invitation.getStatus().name(),
					invitation.getCreatedAt()
			));
		}
		return result;
	}

	@Transactional
	public TeamInvitationActionResponse acceptInvitation(String actorUserId, Long invitationId) {
		TeamInvitationEntity invitation = findInvitationForInvitee(actorUserId, invitationId);
		if (invitation.getStatus() != TeamInvitationStatus.PENDING) {
			throw new InvalidTeamInvitationStateException("이미 처리된 초대입니다");
		}

		TeamEntity team = teamRepository.findById(invitation.getTeamId())
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));

		if (!teamMemberRepository.existsByTeamIdAndUserId(invitation.getTeamId(), actorUserId)) {
			teamMemberRepository.save(TeamMemberEntity.of(invitation.getTeamId(), actorUserId, TeamMemberRole.MEMBER));
		}

		invitation.accept();
		teamInvitationRepository.save(invitation);
		return new TeamInvitationActionResponse(
				String.valueOf(invitation.getId()),
				String.valueOf(invitation.getTeamId()),
				team.getName(),
				invitation.getStatus().name(),
				invitation.getRespondedAt()
		);
	}

	@Transactional
	public TeamInvitationActionResponse rejectInvitation(String actorUserId, Long invitationId) {
		TeamInvitationEntity invitation = findInvitationForInvitee(actorUserId, invitationId);
		if (invitation.getStatus() != TeamInvitationStatus.PENDING) {
			throw new InvalidTeamInvitationStateException("이미 처리된 초대입니다");
		}

		TeamEntity team = teamRepository.findById(invitation.getTeamId())
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));

		invitation.reject();
		teamInvitationRepository.save(invitation);
		return new TeamInvitationActionResponse(
				String.valueOf(invitation.getId()),
				String.valueOf(invitation.getTeamId()),
				team.getName(),
				invitation.getStatus().name(),
				invitation.getRespondedAt()
		);
	}

	@Transactional
	public TeamSummaryResponse removeMember(String actorUserId, Long teamId, String targetUserId) {
		TeamEntity team = teamRepository.findById(teamId)
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));
		TeamMemberEntity actorMembership = teamMemberRepository.findByTeamIdAndUserId(teamId, actorUserId)
				.orElseThrow(() -> new ForbiddenTeamAccessException("팀 멤버만 삭제할 수 있습니다"));
		if (actorMembership.getRole() != TeamMemberRole.OWNER) {
			throw new OwnerPermissionRequiredException("팀장만 팀원을 삭제할 수 있습니다");
		}
		TeamMemberEntity targetMembership = teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId)
				.orElseThrow(() -> new IllegalArgumentException("삭제할 팀원을 찾을 수 없습니다"));
		if (targetMembership.getRole() == TeamMemberRole.OWNER) {
			throw new IllegalArgumentException("팀장은 삭제할 수 없습니다");
		}
		teamMemberRepository.delete(targetMembership);
		return new TeamSummaryResponse(String.valueOf(team.getId()), team.getName());
	}

	@Transactional
	public void deleteTeam(String actorUserId, Long teamId) {
		TeamEntity team = teamRepository.findById(teamId)
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));
		TeamMemberEntity actorMembership = teamMemberRepository.findByTeamIdAndUserId(teamId, actorUserId)
				.orElseThrow(() -> new ForbiddenTeamAccessException("팀 멤버만 팀을 삭제할 수 있습니다"));
		if (actorMembership.getRole() != TeamMemberRole.OWNER) {
			throw new OwnerPermissionRequiredException("팀장만 팀을 삭제할 수 있습니다");
		}
		if (teamApiKeyRepository.existsByTeamId(teamId)) {
			throw new TeamDeletionBlockedException("팀 API 키를 모두 삭제한 뒤 팀을 삭제할 수 있습니다");
		}
		teamInvitationRepository.deleteAllByTeamId(teamId);
		teamMemberRepository.deleteAllByTeamId(teamId);
		teamRepository.delete(team);
	}

	private TeamInvitationEntity findInvitationForInvitee(String actorUserId, Long invitationId) {
		if (!StringUtils.hasText(actorUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		return teamInvitationRepository.findByIdAndInviteeId(invitationId, actorUserId.trim())
				.orElseThrow(() -> new TeamInvitationNotFoundException("초대를 찾을 수 없습니다"));
	}
}
