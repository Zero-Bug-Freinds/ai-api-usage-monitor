package com.zerobugfreinds.team_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalTeamInvitationDecisionRequest(
		@NotBlank(message = "inviteeUserId는 필수입니다")
		String inviteeUserId,
		@NotNull(message = "decision은 필수입니다")
		Decision decision
) {
	public enum Decision {
		ACCEPT,
		REJECT
	}
}
