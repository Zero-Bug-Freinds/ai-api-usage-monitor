package com.zerobugfreinds.ai_agent_service.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record EventDebugDto(
		LocalDateTime receivedAt,
		String eventType,
		Map<String, String> headers,
		String payload
) {
}
