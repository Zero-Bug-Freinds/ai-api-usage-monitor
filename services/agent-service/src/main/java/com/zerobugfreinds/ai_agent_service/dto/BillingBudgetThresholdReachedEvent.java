package com.zerobugfreinds.ai_agent_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BillingBudgetThresholdReachedEvent(
		int schemaVersion,
		Instant occurredAt,
		LocalDate monthStart,
		BigDecimal thresholdPct,
		BigDecimal monthlyTotalUsd,
		BigDecimal monthlyBudgetUsd
) {
}
