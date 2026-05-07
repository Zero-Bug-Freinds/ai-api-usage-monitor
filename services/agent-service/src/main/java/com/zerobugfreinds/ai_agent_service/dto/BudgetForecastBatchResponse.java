package com.zerobugfreinds.ai_agent_service.dto;

import java.util.List;

public record BudgetForecastBatchResponse(
		List<Item> results
) {
	public record Item(
			Long keyId,
			BudgetForecastResponse forecast
	) {
	}
}
