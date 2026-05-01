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
		@NotNull(message = "billingCycleEndDate는 필수입니다")
		LocalDate billingCycleEndDate,
		List<BigDecimal> recentDailySpendUsd
) {
}
