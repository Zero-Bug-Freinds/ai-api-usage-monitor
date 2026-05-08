package com.zerobugfreinds.ai_agent_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BudgetForecastRequest(
		@NotBlank(message = "userId는 필수입니다")
		String userId,
		String teamId,
		Long keyId,
		@NotNull(message = "monthlyBudgetUsd는 필수입니다")
		@DecimalMin(value = "0.0", inclusive = true, message = "monthlyBudgetUsd는 0 이상이어야 합니다")
		BigDecimal monthlyBudgetUsd,
		@NotNull(message = "currentSpendUsd는 필수입니다")
		@DecimalMin(value = "0.0", inclusive = true, message = "currentSpendUsd는 0 이상이어야 합니다")
		BigDecimal currentSpendUsd,
		@NotNull(message = "remainingTokens는 필수입니다")
		@DecimalMin(value = "0", inclusive = true, message = "remainingTokens는 0 이상이어야 합니다")
		Long remainingTokens,
		@NotNull(message = "averageDailyTokenUsage는 필수입니다")
		@DecimalMin(value = "0.0", inclusive = false, message = "averageDailyTokenUsage는 0보다 커야 합니다")
		BigDecimal averageDailyTokenUsage,
		@NotNull(message = "averageDailySpendUsd는 필수입니다")
		@DecimalMin(value = "0.0", inclusive = false, message = "averageDailySpendUsd는 0보다 커야 합니다")
		BigDecimal averageDailySpendUsd,
		/** When null, billing-cycle metrics in the response are null (no billing-cycle event / not configured). */
		LocalDate billingCycleEndDate,
		List<BigDecimal> recentDailySpendUsd,
		List<Long> recentDailyTokenUsage7d,
		List<ModelUsageShare> modelUsageDistribution7d,
		List<Long> hourlyTokenUsage24h
) {
	public record ModelUsageShare(
			String model,
			BigDecimal percentage
	) {
	}
}
