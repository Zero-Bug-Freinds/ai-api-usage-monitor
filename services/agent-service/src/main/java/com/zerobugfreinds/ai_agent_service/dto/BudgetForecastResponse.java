package com.zerobugfreinds.ai_agent_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BudgetForecastResponse(
		String healthStatus,
		String healthStatusLabel,
		String riskCriteria,
		String confidenceLevel,
		String confidenceCriteria,
		LocalDate predictedRunOutDate,
		long daysUntilRunOut,
		Long daysUntilBillingCycleEnd,
		Long billingDateGapDays,
		BigDecimal budgetUtilizationPercent,
		String assistantMessage,
		List<String> recommendedActions,
		String anomalySummary,
		String routingRecommendation,
		BigDecimal estimatedRoutingSavingsPercent
) {
}
