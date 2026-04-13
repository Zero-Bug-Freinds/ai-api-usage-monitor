package com.zerobugfreinds.team_service.dto;

import java.time.Instant;

/**
 * 내부 서비스 간 조회용 팀 상세 응답.
 */
public record InternalTeamDetailResponse(
		String id,
		String name,
		String createdBy,
		Instant createdAt
) {
}
