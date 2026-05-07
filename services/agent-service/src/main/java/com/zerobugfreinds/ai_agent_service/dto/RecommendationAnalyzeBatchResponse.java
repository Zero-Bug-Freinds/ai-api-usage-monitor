package com.zerobugfreinds.ai_agent_service.dto;

import java.util.List;

public record RecommendationAnalyzeBatchResponse(
		List<Item> results
) {
	public record Item(
			String keyId,
			RecommendationQueryResponse recommendation
	) {
	}
}
