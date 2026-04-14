package com.zerobugfreinds.team_service.event;

import java.time.Instant;

/**
 * 팀 멤버 초대 완료 이벤트.
 */
public record TeamMemberAddedEvent(
		String receiverId,
		String inviterId,
		String teamId,
		String teamName,
		Instant createdAt
) {
}
