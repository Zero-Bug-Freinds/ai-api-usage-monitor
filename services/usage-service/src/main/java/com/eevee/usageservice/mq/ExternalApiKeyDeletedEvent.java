package com.eevee.usageservice.mq;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * identity-service 가 물리 삭제 시 발행하는 페이로드.
 * {@code retainLogs} 가 JSON 에 없으면 null 로 두고, 소비 측에서 true(로그 유지)로 간주한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExternalApiKeyDeletedEvent(
        @JsonProperty("eventType")
        String eventType,

        @JsonProperty("userId")
        Long userId,

        @JsonProperty("apiKeyId")
        Long apiKeyId,

        @JsonProperty("occurredAt")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,

        @JsonProperty("retainLogs")
        Boolean retainLogs,

        @JsonProperty("provider")
        String provider,

        @JsonProperty("alias")
        String alias
) {
}
