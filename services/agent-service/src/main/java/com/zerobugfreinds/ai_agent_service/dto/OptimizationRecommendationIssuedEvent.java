package com.zerobugfreinds.ai_agent_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OptimizationRecommendationIssuedEvent(
		String eventId,
		String eventType,
		String schemaVersion,
		Instant occurredAt,
		String producer,
		Tenant tenant,
		Target target,
		AnalysisWindow analysisWindow,
		Signals signals,
		Recommendation recommendation,
		Delivery delivery
) {
	public record Tenant(
			RecommendationScopeType scopeType,
			String scopeId
	) {
	}

	public record Target(
			String keyId,
			RecommendationScopeType keyType,
			String provider,
			String currentModel
	) {
	}

	public record AnalysisWindow(
			Instant startAt,
			Instant endAt,
			String timezone
	) {
	}

	public record Signals(
			Long totalRequests,
			Long totalInputTokens,
			Long totalOutputTokens,
			BigDecimal inputOutputRatio,
			Long avgLatencyMs,
			BigDecimal errorRatePct
	) {
	}

	public record Recommendation(
			RecommendationReasonCode reasonCode,
			RecommendationConfidenceLevel confidenceLevel,
			String primaryModel,
			List<String> candidateModels,
			BigDecimal estimatedMonthlyCostCurrentUsd,
			BigDecimal estimatedMonthlyCostRecommendedUsd,
			BigDecimal estimatedSavingsPct,
			String explanation
	) {
	}

	public record Delivery(
			String dedupeKey
	) {
	}
}
