package com.zerobugfreinds.team_service.dto;

import java.time.Instant;

public record TeamSummaryResponse(
		String id,
		String name,
		Instant createdAt
) {
}
