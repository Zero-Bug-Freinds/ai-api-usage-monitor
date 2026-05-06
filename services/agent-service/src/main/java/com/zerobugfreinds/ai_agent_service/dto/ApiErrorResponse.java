package com.zerobugfreinds.ai_agent_service.dto;

import java.time.Instant;

public record ApiErrorResponse(
		String code,
		String message,
		Instant timestamp
) {
}
