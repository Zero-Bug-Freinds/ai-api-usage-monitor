package com.zerobugfreinds.ai_agent_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BillingCostCorrectedEvent(
		int schemaVersion,
		Instant occurredAt,
		UUID correctionEventId,
		String userId,
		String apiKeyId,
		LocalDate monthStartDate,
		BigDecimal appliedDeltaCostUsd,
		LocalDate aggDate,
		String provider,
		String model,
		BigDecimal optionalCorrectedTotalUsdForScope,
		UUID relatedUsageEventId
) {
}
