package com.zerobugfreinds.team_service.dto;

import java.time.Instant;

public record TeamInvitationActionResponse(
		String invitationId,
		String teamId,
		String teamName,
		String status,
		Instant respondedAt
) {
}
