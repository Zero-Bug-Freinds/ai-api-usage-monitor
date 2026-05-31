package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import com.zerobugfreinds.team_service.domain.TeamMemberRole;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.entity.TeamMemberEntity;
import com.zerobugfreinds.team_service.entity.IdentityUserSyncEntity;
import com.zerobugfreinds.team_service.repository.IdentityUserSyncRepository;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamInvitationRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Identity 탈퇴 이벤트를 수신했을 때 team-service 로컬 데이터 정리를 담당한다.
 * <ul>
 *   <li>팀장(OWNER): 소유 팀 전체 삭제(팀 API 키 즉시 삭제 후 팀 삭제)</li>
 *   <li>팀원(MEMBER): 해당 팀에서 멤버십만 제거</li>
 * </ul>
 */
@Service
public class UserAccountDeletionCleanupService {

	private final TeamMemberRepository teamMemberRepository;
	private final TeamInvitationRepository teamInvitationRepository;
	private final TeamApiKeyRepository teamApiKeyRepository;
	private final IdentityUserSyncRepository identityUserSyncRepository;
	private final IdentityUserSyncService identityUserSyncService;
	private final TeamService teamService;
	private final TeamApiKeyService teamApiKeyService;

	public UserAccountDeletionCleanupService(
			TeamMemberRepository teamMemberRepository,
			TeamInvitationRepository teamInvitationRepository,
			TeamApiKeyRepository teamApiKeyRepository,
			IdentityUserSyncRepository identityUserSyncRepository,
			IdentityUserSyncService identityUserSyncService,
			TeamService teamService,
			TeamApiKeyService teamApiKeyService
	) {
		this.teamMemberRepository = teamMemberRepository;
		this.teamInvitationRepository = teamInvitationRepository;
		this.teamApiKeyRepository = teamApiKeyRepository;
		this.identityUserSyncRepository = identityUserSyncRepository;
		this.identityUserSyncService = identityUserSyncService;
		this.teamService = teamService;
		this.teamApiKeyService = teamApiKeyService;
	}

	@Transactional
	public CleanupResult cleanup(UserAccountDeletionRequestedEvent event) {
		if (event == null) {
			throw new IllegalArgumentException("event is required");
		}
		List<String> lookupCandidates = resolveLookupCandidates(event);
		if (lookupCandidates.isEmpty()) {
			throw new IllegalArgumentException("userId lookup candidates are required");
		}
		String actorUserId = resolveActorUserId(lookupCandidates);

		Set<Long> ownerTeamIds = new LinkedHashSet<>();
		for (String candidate : lookupCandidates) {
			for (TeamMemberEntity membership : teamMemberRepository.findAllByUserIdInAndRole(
					List.of(candidate),
					TeamMemberRole.OWNER
			)) {
				ownerTeamIds.add(membership.getTeamId());
			}
		}

		int deletedTeams = 0;
		int deletedTeamApiKeys = 0;
		for (Long teamId : ownerTeamIds) {
			List<TeamApiKeyEntity> apiKeys = teamApiKeyRepository.findAllByTeamIdOrderByCreatedAtDesc(teamId);
			for (TeamApiKeyEntity apiKey : apiKeys) {
				teamApiKeyService.delete(actorUserId, teamId, apiKey.getId(), 0, false);
				deletedTeamApiKeys++;
			}
			teamService.deleteTeamForAccountDeletion(actorUserId, teamId);
			deletedTeams++;
		}

		long deletedMemberMemberships = 0;
		for (String candidate : lookupCandidates) {
			for (TeamMemberEntity membership : teamMemberRepository.findAllByUserIdIn(List.of(candidate))) {
				if (membership.getRole() == TeamMemberRole.OWNER || ownerTeamIds.contains(membership.getTeamId())) {
					continue;
				}
				teamService.removeMemberForAccountDeletion(candidate, membership.getTeamId());
				deletedMemberMemberships++;
			}
		}

		long deletedInvitations = 0;
		for (String candidate : lookupCandidates) {
			deletedInvitations += teamInvitationRepository.deleteByInviteeIdOrInviterId(candidate, candidate);
		}

		long deletedMembershipRows = 0;
		for (String candidate : lookupCandidates) {
			deletedMembershipRows += teamMemberRepository.deleteByUserId(candidate);
		}

		long deletedSyncRows = purgeIdentityUserSync(lookupCandidates);

		return new CleanupResult(
				deletedTeams,
				deletedTeamApiKeys,
				deletedMemberMemberships,
				deletedInvitations,
				deletedMembershipRows,
				deletedSyncRows
		);
	}

	private long purgeIdentityUserSync(List<String> lookupCandidates) {
		Set<String> syncIds = new LinkedHashSet<>();
		for (String candidate : lookupCandidates) {
			if (!StringUtils.hasText(candidate)) {
				continue;
			}
			String trimmed = candidate.trim();
			syncIds.add(trimmed);
			if (trimmed.contains("@")) {
				String email = trimmed.toLowerCase(Locale.ROOT);
				syncIds.add(email);
				identityUserSyncRepository.findByEmailIgnoreCase(email)
						.map(IdentityUserSyncEntity::getUserId)
						.filter(StringUtils::hasText)
						.ifPresent(syncIds::add);
			} else {
				identityUserSyncRepository.findById(trimmed)
						.map(IdentityUserSyncEntity::getEmail)
						.filter(StringUtils::hasText)
						.map(email -> email.trim().toLowerCase(Locale.ROOT))
						.ifPresent(syncIds::add);
			}
		}
		long deleted = 0;
		for (String syncId : syncIds) {
			if (identityUserSyncRepository.existsById(syncId)) {
				identityUserSyncRepository.deleteById(syncId);
				deleted++;
			}
		}
		return deleted;
	}

	private List<String> resolveLookupCandidates(UserAccountDeletionRequestedEvent event) {
		Set<String> merged = new LinkedHashSet<>();
		if (StringUtils.hasText(event.userEmail())) {
			String email = event.userEmail().trim();
			merged.addAll(identityUserSyncService.resolveMembershipLookupCandidates(email));
			merged.add(email);
		}
		String numericId = String.valueOf(event.identityUserId());
		merged.add(numericId);
		merged.addAll(identityUserSyncService.resolveMembershipLookupCandidates(numericId));
		List<String> candidates = new ArrayList<>();
		for (String value : merged) {
			if (StringUtils.hasText(value)) {
				candidates.add(value.trim());
			}
		}
		return candidates;
	}

	private static String resolveActorUserId(List<String> lookupCandidates) {
		for (String candidate : lookupCandidates) {
			if (candidate.contains("@")) {
				return candidate;
			}
		}
		return lookupCandidates.getFirst();
	}

	public record CleanupResult(
			int deletedTeams,
			int deletedTeamApiKeys,
			long removedMemberMemberships,
			long deletedInvitations,
			long deletedMembershipRows,
			long deletedIdentityUserSyncRows
	) {
	}
}
