package com.zerobugfreinds.team_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;

/**
 * identity-service 사용자 생성/갱신 이벤트를 느슨하게 수신하기 위한 DTO.
 */
public record IdentityUserSyncEvent(
        @JsonAlias({"eventType", "type"}) String eventType,
        @JsonAlias({"userId", "identityUserId", "id"}) String userId,
        @JsonAlias({"email", "userEmail"}) String email,
        @JsonAlias({"name", "displayName", "username"}) String name,
        @JsonAlias({"occurredAt", "createdAt", "updatedAt"}) Instant occurredAt
) {
}
