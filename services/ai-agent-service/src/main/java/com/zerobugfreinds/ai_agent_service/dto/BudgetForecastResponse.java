package com.zerobugfreinds.ai_agent_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BudgetForecastResponse(
		String healthStatus,
		LocalDate predictedRunOutDate,
		long daysUntilRunOut,
		long daysUntilBillingCycleEnd,
		long billingDateGapDays,
		BigDecimal budgetUtilizationPercent,
		String assistantMessage,
		List<String> recommendedActions
) {
}
