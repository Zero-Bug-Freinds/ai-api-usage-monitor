package com.zerobugfreinds.team_service.dto;

import java.math.BigDecimal;

public record InternalBillingTeamApiKeyResponse(
		String apiKeyId,
		String apiKeySource,
		String provider,
		String alias,
		BigDecimal monthlyBudgetUsd
) {
}
