package com.zerobugfreinds.team_service.dto;

/**
 * 내부 서비스 간 팀 멤버십 검증 응답.
 */
public record InternalTeamMembershipVerifyResponse(
        Long teamId,
        String userId,
        boolean isValid
) {
}
