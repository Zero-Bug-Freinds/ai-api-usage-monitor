package com.eevee.usageservice.mq;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record TeamApiKeyChangedEvent(
        @JsonProperty("eventType")
        String eventType,

        @JsonProperty("teamId")
        String teamId,

        @JsonProperty("actorUserId")
        String actorUserId,

        @JsonProperty("occurredAt")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,

        @JsonProperty("apiKeyId")
        Long apiKeyId,

        @JsonProperty("provider")
        String provider,

        @JsonProperty("alias")
        String alias
) {
}
