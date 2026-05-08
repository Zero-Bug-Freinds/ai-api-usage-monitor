package com.eevee.usageservice.mq;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record TeamApiKeyDeletedEvent(
        @JsonProperty("eventType")
        String eventType,
        @JsonProperty("occurredAt")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,
        @JsonProperty("apiKeyId")
        Long apiKeyId,
        @JsonProperty("teamId")
        Long teamId,
        @JsonProperty("provider")
        String provider,
        @JsonProperty("alias")
        String alias,
        @JsonProperty("actorUserId")
        String actorUserId
) {
}
