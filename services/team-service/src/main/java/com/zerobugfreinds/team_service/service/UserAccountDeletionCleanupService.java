package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.repository.TeamInvitationRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Identity 탈퇴 이벤트를 수신했을 때 team-service 로컬 데이터 정리를 담당한다.
 */
@Service
public class UserAccountDeletionCleanupService {

	private final TeamMemberRepository teamMemberRepository;
	private final TeamInvitationRepository teamInvitationRepository;

	public UserAccountDeletionCleanupService(
			TeamMemberRepository teamMemberRepository,
			TeamInvitationRepository teamInvitationRepository
	) {
		this.teamMemberRepository = teamMemberRepository;
		this.teamInvitationRepository = teamInvitationRepository;
	}

	@Transactional
	public CleanupResult cleanupByUserId(String userId) {
		if (!StringUtils.hasText(userId)) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		String normalized = userId.trim();
		long deletedInvitations = teamInvitationRepository.deleteByInviteeIdOrInviterId(normalized, normalized);
		long deletedMemberships = teamMemberRepository.deleteByUserId(normalized);
		return new CleanupResult(deletedMemberships, deletedInvitations);
	}

	public record CleanupResult(long deletedMemberships, long deletedInvitations) {
	}
}
