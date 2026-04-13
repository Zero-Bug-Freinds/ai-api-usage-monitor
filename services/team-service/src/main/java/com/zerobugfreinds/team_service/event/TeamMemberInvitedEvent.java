package com.zerobugfreinds.team_service.event;

import java.time.Instant;

public record TeamMemberInvitedEvent(
		String invitationId,
		String receiverId,
		String inviterId,
		String teamId,
		String teamName,
		Instant createdAt
) {
}
