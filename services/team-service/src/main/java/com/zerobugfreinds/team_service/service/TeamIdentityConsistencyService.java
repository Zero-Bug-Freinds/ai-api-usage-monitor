package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.dto.TeamIdentityConsistencyResponse;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class TeamIdentityConsistencyService {
	private static final String REMEDIATION_NOTE =
			"TODO: zombie userId 발견 시 팀 멤버 강제 탈퇴 및 초대/소유권 재검증 배치로 정리하세요.";

	private final TeamMemberRepository teamMemberRepository;
	private final IdentityUserLookupClient identityUserLookupClient;

	public TeamIdentityConsistencyService(
			TeamMemberRepository teamMemberRepository,
			IdentityUserLookupClient identityUserLookupClient
	) {
		this.teamMemberRepository = teamMemberRepository;
		this.identityUserLookupClient = identityUserLookupClient;
	}

	@Transactional(readOnly = true)
	public TeamIdentityConsistencyResponse findZombieUsers() {
		List<String> teamMemberUserIds = teamMemberRepository.findDistinctUserIds().stream()
				.filter(userId -> userId != null && !userId.isBlank())
				.map(String::trim)
				.distinct()
				.toList();
		Set<String> existingUserIds = identityUserLookupClient.findExistingUserIds(teamMemberUserIds);
		List<String> zombieUserIds = teamMemberUserIds.stream()
				.filter(userId -> !existingUserIds.contains(userId))
				.toList();
		return new TeamIdentityConsistencyResponse(
				teamMemberUserIds.size(),
				zombieUserIds.size(),
				zombieUserIds,
				Instant.now(),
				REMEDIATION_NOTE
		);
	}
}
