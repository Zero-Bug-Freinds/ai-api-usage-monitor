package com.zerobugfreinds.ai_agent_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RecommendationAnalyzeBatchRequest(
		@NotEmpty(message = "requests는 최소 1건 이상이어야 합니다")
		List<@Valid RecommendationAnalyzeRequest> requests
) {
}
