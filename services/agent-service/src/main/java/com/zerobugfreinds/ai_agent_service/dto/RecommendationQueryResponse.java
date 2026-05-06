package com.zerobugfreinds.ai_agent_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecommendationQueryResponse(
		String keyId,
		RecommendationScopeType keyType,
		String status,
		Instant generatedAt,
		MetricsContext metricsContext,
		RecommendationDetails recommendationDetails
) {
	public record MetricsContext(
			int analysisWindowDays,
			Long totalTokensUsed,
			String inputOutputRatio,
			Long averageLatencyMs,
			Long totalRequests
	) {
	}

	public record RecommendationDetails(
			String title,
			RecommendationReasonCode reasonCode,
			String reasonMessage,
			RecommendationConfidenceLevel confidenceLevel,
			String disclaimer,
			BigDecimal estimatedSavingsPct,
			List<CandidateModel> candidates
	) {
	}

	public record CandidateModel(
			String modelName,
			BigDecimal expectedCostDiffPct,
			BigDecimal expectedMonthlyCostUsd,
			String keyFeature
	) {
	}
}
