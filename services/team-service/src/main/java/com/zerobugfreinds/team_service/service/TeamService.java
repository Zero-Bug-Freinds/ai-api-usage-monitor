package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamMemberRole;
import com.zerobugfreinds.team_service.domain.TeamInvitationStatus;
import com.zerobugfreinds.team_service.dto.InternalTeamDetailResponse;
import com.zerobugfreinds.team_service.dto.InternalBillingTeamApiKeyResponse;
import com.zerobugfreinds.team_service.dto.InternalBillingTeamSummaryResponse;
import com.zerobugfreinds.team_service.dto.InternalTeamInvitationDecisionRequest;
import com.zerobugfreinds.team_service.dto.InternalTeamMembershipVerifyResponse;
import com.zerobugfreinds.team_service.dto.TeamResponse;
import com.zerobugfreinds.team_service.dto.TeamInvitationActionResponse;
import com.zerobugfreinds.team_service.dto.TeamInvitationResponse;
import com.zerobugfreinds.team_service.dto.TeamSummaryResponse;
import com.zerobugfreinds.team_service.entity.TeamEntity;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.entity.TeamInvitationEntity;
import com.zerobugfreinds.team_service.entity.TeamMemberEntity;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamCreatedEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamDeletedEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamInvitationAcceptedEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamInvitationRejectedEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamInviteCreatedEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamMemberJoinedEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamMemberRemovedEvent;
import com.zerobugfreinds.team_service.exception.DuplicateTeamMemberException;
import com.zerobugfreinds.team_service.exception.DuplicateTeamInvitationException;
import com.zerobugfreinds.team_service.exception.ForbiddenTeamAccessException;
import com.zerobugfreinds.team_service.exception.InvalidTeamInvitationStateException;
import com.zerobugfreinds.team_service.exception.OwnerPermissionRequiredException;
import com.zerobugfreinds.team_service.exception.TeamDeletionBlockedException;
import com.zerobugfreinds.team_service.exception.TeamNotFoundException;
import com.zerobugfreinds.team_service.exception.TeamInvitationNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamInvitationRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import com.zerobugfreinds.team_service.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import java.time.temporal.ChronoUnit;

@Service
public class TeamService {
	private final TeamRepository teamRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final TeamInvitationRepository teamInvitationRepository;
	private final TeamApiKeyRepository teamApiKeyRepository;
	private final TeamDomainEventPublisher teamDomainEventPublisher;
	private final IdentityUserSyncService identityUserSyncService;
	private long invitationExpirationDays = 7;
	private long invitationCleanupRetentionDays = 30;

	public TeamService(
			TeamRepository teamRepository,
			TeamMemberRepository teamMemberRepository,
			TeamInvitationRepository teamInvitationRepository,
			TeamApiKeyRepository teamApiKeyRepository,
			TeamDomainEventPublisher teamDomainEventPublisher,
			IdentityUserSyncService identityUserSyncService
	) {
		this.teamRepository = teamRepository;
		this.teamMemberRepository = teamMemberRepository;
		this.teamInvitationRepository = teamInvitationRepository;
		this.teamApiKeyRepository = teamApiKeyRepository;
		this.teamDomainEventPublisher = teamDomainEventPublisher;
		this.identityUserSyncService = identityUserSyncService;
	}

	@Value("${team.invitation.expiration-days:7}")
	public void setInvitationExpirationDays(long invitationExpirationDays) {
		this.invitationExpirationDays = invitationExpirationDays;
	}

	@Value("${team.invitation.cleanup-retention-days:30}")
	public void setInvitationCleanupRetentionDays(long invitationCleanupRetentionDays) {
		this.invitationCleanupRetentionDays = invitationCleanupRetentionDays;
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
		publish(TeamCreatedEvent.of(String.valueOf(saved.getId()), saved.getName(), actorUserId, saved.getCreatedAt()));
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

	@Transactional(readOnly = true)
	public Page<TeamResponse> getTeams(String keyword, Pageable pageable) {
		Page<TeamEntity> page;
		if (!StringUtils.hasText(keyword)) {
			page = teamRepository.findAll(pageable);
		} else {
			page = teamRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable);
		}
		return page.map(team -> new TeamResponse(
				String.valueOf(team.getId()),
				team.getName(),
				teamMemberRepository.countByTeamId(team.getId())
		));
	}

	@Transactional
	public TeamSummaryResponse inviteMember(String actorUserId, Long teamId, String inviteeUserId) {
		if (!StringUtils.hasText(actorUserId) || !StringUtils.hasText(inviteeUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		String invitee = inviteeUserId.trim();
		TeamEntity team = teamRepository.findById(teamId)
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));

		if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, actorUserId)) {
			throw new ForbiddenTeamAccessException("팀 멤버만 초대할 수 있습니다");
		}
		if (teamMemberRepository.existsByTeamIdAndUserId(teamId, invitee)) {
			throw new DuplicateTeamMemberException("이미 팀에 참여 중인 사용자입니다");
		}
		if (!existsUserInTeamDb(invitee)) {
			throw new IllegalArgumentException("존재하는 사용자만 초대할 수 있습니다");
		}
		if (teamInvitationRepository.existsByTeamIdAndInviteeIdAndStatus(teamId, invitee, TeamInvitationStatus.PENDING)) {
			throw new DuplicateTeamInvitationException("이미 대기 중인 팀 초대가 있습니다");
		}

		TeamInvitationEntity invitation = teamInvitationRepository.save(
				TeamInvitationEntity.create(teamId, actorUserId.trim(), invitee)
		);
		publish(TeamInviteCreatedEvent.of(
				String.valueOf(invitation.getId()),
				invitee,
				actorUserId.trim(),
				team.getId(),
				team.getName(),
				invitation.getCreatedAt()
		));
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
	public List<InternalBillingTeamSummaryResponse> getBillingTeamSummariesInternal(String userId) {
		if (!StringUtils.hasText(userId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		String normalizedUserId = userId.trim();
		List<TeamMemberEntity> memberships = teamMemberRepository.findAllByUserId(normalizedUserId);
		if (memberships.isEmpty()) {
			return List.of();
		}

		List<Long> teamIds = memberships.stream()
				.map(TeamMemberEntity::getTeamId)
				.distinct()
				.toList();
		Map<Long, TeamEntity> teamById = teamRepository.findAllById(teamIds).stream()
				.collect(Collectors.toMap(TeamEntity::getId, team -> team));
		Map<Long, List<TeamApiKeyEntity>> apiKeysByTeamId = teamApiKeyRepository
				.findAllByTeamIdInOrderByTeamIdAscCreatedAtDesc(teamIds)
				.stream()
				.collect(Collectors.groupingBy(TeamApiKeyEntity::getTeamId));

		List<InternalBillingTeamSummaryResponse> result = new ArrayList<>();
		for (Long teamId : teamIds) {
			TeamEntity team = teamById.get(teamId);
			if (team == null) {
				continue;
			}
			List<InternalBillingTeamApiKeyResponse> keyResponses = apiKeysByTeamId
					.getOrDefault(teamId, List.of())
					.stream()
					.map(apiKey -> new InternalBillingTeamApiKeyResponse(
							apiKey.getId(),
							apiKey.getProvider().name(),
							apiKey.getKeyAlias(),
							apiKey.getMonthlyBudgetUsd()
					))
					.toList();
			java.math.BigDecimal totalMonthlyBudgetUsd = keyResponses.stream()
					.map(InternalBillingTeamApiKeyResponse::monthlyBudgetUsd)
					.filter(java.util.Objects::nonNull)
					.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
			result.add(new InternalBillingTeamSummaryResponse(
					String.valueOf(team.getId()),
					team.getName(),
					totalMonthlyBudgetUsd,
					keyResponses,
					keyResponses
			));
		}
		return result;
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
	public InternalTeamMembershipVerifyResponse verifyTeamMembershipInternal(Long teamId, String userId) {
		boolean isValid = teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
		return new InternalTeamMembershipVerifyResponse(teamId, userId, isValid);
	}

	@Transactional(readOnly = true)
	public boolean isTeamOwner(String actorUserId, Long teamId) {
		if (!StringUtils.hasText(actorUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		teamRepository.findById(teamId)
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));
		TeamMemberEntity membership = teamMemberRepository.findByTeamIdAndUserId(teamId, actorUserId)
				.orElseThrow(() -> new ForbiddenTeamAccessException("팀 멤버만 조회할 수 있습니다"));
		return membership.getRole() == TeamMemberRole.OWNER;
	}

	@Transactional(readOnly = true)
	public List<TeamInvitationResponse> getMyPendingInvitations(String actorUserId) {
		return getMyInvitations(actorUserId, false);
	}

	@Transactional(readOnly = true)
	public List<TeamInvitationResponse> getMyInvitations(String actorUserId, boolean includeExpired) {
		if (!StringUtils.hasText(actorUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		expireStaleInvitations(Instant.now());
		List<TeamInvitationEntity> invitations = includeExpired
				? teamInvitationRepository.findAllByInviteeIdAndStatusInOrderByCreatedAtDesc(
						actorUserId,
						List.of(TeamInvitationStatus.PENDING, TeamInvitationStatus.EXPIRED)
				)
				: teamInvitationRepository.findAllByInviteeIdAndStatusOrderByCreatedAtDesc(actorUserId, TeamInvitationStatus.PENDING);
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
					invitation.getCreatedAt(),
					invitation.getRespondedAt()
			));
		}
		return result;
	}

	@Transactional
	public TeamInvitationActionResponse acceptInvitation(String actorUserId, Long invitationId) {
		TeamInvitationEntity invitation = findInvitationForInvitee(actorUserId, invitationId);
		expireInvitationIfNeeded(invitation, Instant.now());
		if (invitation.getStatus() != TeamInvitationStatus.PENDING) {
			throw new InvalidTeamInvitationStateException("이미 처리되었거나 만료된 초대입니다");
		}

		TeamEntity team = teamRepository.findById(invitation.getTeamId())
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));

		if (!teamMemberRepository.existsByTeamIdAndUserId(invitation.getTeamId(), actorUserId)) {
			teamMemberRepository.save(TeamMemberEntity.of(invitation.getTeamId(), actorUserId, TeamMemberRole.MEMBER));
		}

		invitation.accept();
		teamInvitationRepository.save(invitation);
		Instant respondedAt = invitation.getRespondedAt();
		publish(TeamInvitationAcceptedEvent.of(
				String.valueOf(invitation.getId()),
				actorUserId,
				invitation.getInviterId(),
				invitation.getTeamId(),
				team.getName(),
				respondedAt
		));
		publish(TeamMemberJoinedEvent.of(
				actorUserId,
				invitation.getInviterId(),
				invitation.getTeamId(),
				team.getName(),
				respondedAt
		));
		return new TeamInvitationActionResponse(
				String.valueOf(invitation.getId()),
				String.valueOf(invitation.getTeamId()),
				team.getName(),
				invitation.getStatus().name(),
				respondedAt
		);
	}

	@Transactional
	public TeamInvitationActionResponse rejectInvitation(String actorUserId, Long invitationId) {
		TeamInvitationEntity invitation = findInvitationForInvitee(actorUserId, invitationId);
		expireInvitationIfNeeded(invitation, Instant.now());
		if (invitation.getStatus() != TeamInvitationStatus.PENDING) {
			throw new InvalidTeamInvitationStateException("이미 처리되었거나 만료된 초대입니다");
		}

		TeamEntity team = teamRepository.findById(invitation.getTeamId())
				.orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));

		invitation.reject();
		teamInvitationRepository.save(invitation);
		Instant respondedAt = invitation.getRespondedAt();
		publish(TeamInvitationRejectedEvent.of(
				String.valueOf(invitation.getId()),
				actorUserId,
				invitation.getInviterId(),
				invitation.getTeamId(),
				team.getName(),
				respondedAt
		));
		return new TeamInvitationActionResponse(
				String.valueOf(invitation.getId()),
				String.valueOf(invitation.getTeamId()),
				team.getName(),
				invitation.getStatus().name(),
				respondedAt
		);
	}

	@Transactional
	public TeamInvitationActionResponse processInvitationDecisionInternal(
			String inviteeUserId,
			Long invitationId,
			InternalTeamInvitationDecisionRequest.Decision decision
	) {
		if (decision == InternalTeamInvitationDecisionRequest.Decision.ACCEPT) {
			return acceptInvitation(inviteeUserId, invitationId);
		}
		return rejectInvitation(inviteeUserId, invitationId);
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
		publish(TeamMemberRemovedEvent.of(actorUserId, targetUserId, teamId, team.getName(), Instant.now()));
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
		List<String> memberSnapshot = teamMemberRepository.findAllByTeamId(teamId).stream()
				.map(TeamMemberEntity::getUserId)
				.filter(StringUtils::hasText)
				.map(String::trim)
				.sorted()
				.toList();
		Instant deletedAt = Instant.now();
		publish(TeamDeletedEvent.of(actorUserId, teamId, team.getName(), memberSnapshot, deletedAt));
		teamInvitationRepository.deleteAllByTeamId(teamId);
		teamMemberRepository.deleteAllByTeamId(teamId);
		teamRepository.delete(team);
	}

	private void publish(TeamDomainOutboundEvent event) {
		teamDomainEventPublisher.publish(event);
	}

	private TeamInvitationEntity findInvitationForInvitee(String actorUserId, Long invitationId) {
		if (!StringUtils.hasText(actorUserId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		return teamInvitationRepository.findByIdAndInviteeId(invitationId, actorUserId.trim())
				.orElseThrow(() -> new TeamInvitationNotFoundException("초대를 찾을 수 없습니다"));
	}

	@Transactional
	public int expireStaleInvitations() {
		return expireStaleInvitations(Instant.now());
	}

	@Transactional
	int expireStaleInvitations(Instant now) {
		if (invitationExpirationDays <= 0) {
			return 0;
		}
		Instant expirationCutoff = now.minus(invitationExpirationDays, ChronoUnit.DAYS);
		List<TeamInvitationEntity> staleInvitations =
				teamInvitationRepository.findAllByStatusAndCreatedAtBefore(TeamInvitationStatus.PENDING, expirationCutoff);
		for (TeamInvitationEntity invitation : staleInvitations) {
			invitation.expire(now);
		}
		return staleInvitations.size();
	}

	@Transactional
	public int purgeOldInvitations() {
		if (invitationCleanupRetentionDays <= 0) {
			return 0;
		}
		Instant cleanupCutoff = Instant.now().minus(invitationCleanupRetentionDays, ChronoUnit.DAYS);
		return Math.toIntExact(
				teamInvitationRepository.deleteByStatusInAndRespondedAtBefore(
						List.of(
								TeamInvitationStatus.ACCEPTED,
								TeamInvitationStatus.REJECTED,
								TeamInvitationStatus.EXPIRED
						),
						cleanupCutoff
				)
		);
	}

	private void expireInvitationIfNeeded(TeamInvitationEntity invitation, Instant now) {
		if (invitation.getStatus() != TeamInvitationStatus.PENDING || invitationExpirationDays <= 0) {
			return;
		}
		Instant expirationCutoff = now.minus(invitationExpirationDays, ChronoUnit.DAYS);
		if (invitation.getCreatedAt().isBefore(expirationCutoff)) {
			invitation.expire(now);
		}
	}

	private boolean existsUserInTeamDb(String userIdOrEmail) {
		return teamMemberRepository.existsByUserId(userIdOrEmail)
				|| teamInvitationRepository.existsByInviteeIdOrInviterId(userIdOrEmail, userIdOrEmail)
				|| identityUserSyncService.existsUser(userIdOrEmail);
	}
}
