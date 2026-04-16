package com.zerobugfreinds.team_service.dto;

import java.time.Instant;
import java.util.List;

public record TeamIdentityConsistencyResponse(
		int checkedUserCount,
		int zombieUserCount,
		List<String> zombieUserIds,
		Instant checkedAt,
		String remediationNote
) {
}
