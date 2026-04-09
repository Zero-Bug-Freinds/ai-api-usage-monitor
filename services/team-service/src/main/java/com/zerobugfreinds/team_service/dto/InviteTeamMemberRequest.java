package com.zerobugfreinds.team_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteTeamMemberRequest(
		@NotBlank(message = "userId는 필수입니다")
		@Size(max = 255, message = "userId는 255자 이하여야 합니다")
		String userId
) {
}
