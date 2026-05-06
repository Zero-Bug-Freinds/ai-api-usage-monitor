package com.zerobugfreinds.ai_agent_service.dto;

public enum RecommendationReasonCode {
	HEAVY_INPUT_RATIO,
	HEAVY_OUTPUT_RATIO,
	BALANCED_CHAT,
	HIGH_LATENCY,
	OVER_SPEC_USAGE,
	BUDGET_THRESHOLD_REACHED,
	BUDGET_EXCEEDED
}
