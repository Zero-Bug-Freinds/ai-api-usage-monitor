package com.zerobugfreinds.identity_service.dto;

/**
 * team-service 내부 멤버십 검증 응답.
 */
public record InternalTeamMembershipVerifyResponse(
        Long teamId,
        String userId,
        boolean isValid
) {
}
