package com.zerobugfreinds.team_service.dto;

import java.util.List;

public record InternalBillingTeamSummaryResponse(
		String teamId,
		String teamAlias,
		List<InternalBillingTeamApiKeyResponse> apiKeys
) {
}
