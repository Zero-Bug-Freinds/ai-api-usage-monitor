package com.zerobugfreinds.ai_agent_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PolicyRecommendationRequest(
		@NotBlank(message = "userId는 필수입니다")
		String userId,
		String teamId,
		String provider,
		String model,
		@NotNull(message = "monthlyBudgetUsd는 필수입니다")
		@DecimalMin(value = "0.0", inclusive = true, message = "monthlyBudgetUsd는 0 이상이어야 합니다")
		BigDecimal monthlyBudgetUsd,
		@NotNull(message = "currentSpendUsd는 필수입니다")
		@DecimalMin(value = "0.0", inclusive = true, message = "currentSpendUsd는 0 이상이어야 합니다")
		BigDecimal currentSpendUsd
) {
}
