package com.zerobugfreinds.team_service.dto;

import java.math.BigDecimal;

public record InternalBillingTeamApiKeyResponse(
		Long apiKeyId,
		String provider,
		String alias,
		BigDecimal monthlyBudgetUsd
) {
}
