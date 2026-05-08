package com.zerobugfreinds.ai_agent_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Parsed Gemini JSON for budget forecast (primary path when API key is configured).
 */
public record AiBudgetForecastResult(
		LocalDate predictedRunOutDate,
		long daysUntilRunOut,
		String healthStatus,
		BigDecimal budgetUtilizationPercent,
		String assistantMessage,
		List<String> recommendedActions,
		String anomalySummary,
		String routingRecommendation,
		BigDecimal estimatedRoutingSavingsPercent
) {
}
