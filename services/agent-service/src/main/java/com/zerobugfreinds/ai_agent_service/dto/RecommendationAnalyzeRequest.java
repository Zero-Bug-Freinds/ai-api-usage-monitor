package com.zerobugfreinds.ai_agent_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecommendationAnalyzeRequest(
		@NotNull(message = "scopeType은 필수입니다")
		RecommendationScopeType scopeType,
		@NotBlank(message = "scopeId는 필수입니다")
		String scopeId,
		@NotBlank(message = "keyId는 필수입니다")
		String keyId,
		@Min(value = 1, message = "windowDays는 1 이상이어야 합니다")
		@Max(value = 90, message = "windowDays는 90 이하여야 합니다")
		int windowDays,
		@NotBlank(message = "triggeredBy는 필수입니다")
		String triggeredBy
) {
}
