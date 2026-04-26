package com.zerobugfreinds.identity_service.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 팀 컨텍스트 전환 요청.
 */
public record SwitchTeamRequest(
        @NotNull(message = "targetTeamId는 필수입니다")
        Long targetTeamId
) {
}
