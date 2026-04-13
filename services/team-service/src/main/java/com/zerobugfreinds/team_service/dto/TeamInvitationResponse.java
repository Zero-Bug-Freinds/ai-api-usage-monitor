package com.zerobugfreinds.team_service.dto;

import java.time.Instant;

public record TeamInvitationResponse(
		String invitationId,
		String teamId,
		String teamName,
		String inviterId,
		String inviteeId,
		String status,
		Instant createdAt
) {
}
