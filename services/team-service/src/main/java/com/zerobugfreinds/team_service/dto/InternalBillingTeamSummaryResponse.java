package com.zerobugfreinds.team_service.dto;

import java.math.BigDecimal;
import java.util.List;

public record InternalBillingTeamSummaryResponse(
		String teamId,
		String teamAlias,
		BigDecimal monthlyBudgetUsd,
		List<InternalBillingTeamApiKeyResponse> monthlyBudgetsByKey,
		List<InternalBillingTeamApiKeyResponse> apiKeys
) {
}
