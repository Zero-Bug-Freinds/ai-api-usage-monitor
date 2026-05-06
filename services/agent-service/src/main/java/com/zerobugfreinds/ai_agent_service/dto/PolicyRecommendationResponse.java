package com.zerobugfreinds.ai_agent_service.dto;

import java.math.BigDecimal;
import java.util.List;

public record PolicyRecommendationResponse(
		RecommendationLevel recommendationLevel,
		String recommendedAction,
		BigDecimal utilizationRatePercent,
		List<String> reasons
) {
}
