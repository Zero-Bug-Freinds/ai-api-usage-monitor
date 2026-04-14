package com.zerobugfreinds.identity.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * usage / billing / team 이 자기 DB 정리를 끝낸 뒤 identity 로 보내는 확인 이벤트.
 * routing key 는 identity 설정의 account-deletion-ack 와 맞춘다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserAccountDeletionAcknowledgedEvent(
        int schemaVersion,
        Instant occurredAt,
        long identityUserId,
        String userEmail,
        String source
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** {@code billing}, {@code usage}, {@code team} (대소문자 무시). */
    public static final String SOURCE_BILLING = "billing";
    public static final String SOURCE_USAGE = "usage";
    public static final String SOURCE_TEAM = "team";

    public UserAccountDeletionAcknowledgedEvent {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail is required");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        userEmail = userEmail.trim();
        source = source.trim();
    }
}
