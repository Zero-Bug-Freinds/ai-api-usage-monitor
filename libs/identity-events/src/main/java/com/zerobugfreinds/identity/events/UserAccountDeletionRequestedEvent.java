package com.zerobugfreinds.identity.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Identity가 회원 탈퇴 확정 직전에 발행한다. 구독 서비스(usage, billing, team 등)는
 * 자기 DB에서 해당 사용자 데이터를 삭제한다. 스키마 진화 시 {@link #schemaVersion}을 올린다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserAccountDeletionRequestedEvent(
        int schemaVersion,
        Instant occurredAt,
        long identityUserId,
        String userEmail
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public UserAccountDeletionRequestedEvent {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail is required");
        }
        userEmail = userEmail.trim();
    }

    public static UserAccountDeletionRequestedEvent of(long identityUserId, String userEmail) {
        return new UserAccountDeletionRequestedEvent(
                CURRENT_SCHEMA_VERSION,
                Instant.now(),
                identityUserId,
                userEmail.trim()
        );
    }
}
