package com.eevee.usageservice.mq;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamApiKeyStatusChangedEvent(
        @JsonProperty("eventType")
        String eventType,
        @JsonProperty("occurredAt")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,
        @JsonProperty("teamApiKeyId")
        Long teamApiKeyId,
        @JsonProperty("teamId")
        Long teamId,
        @JsonProperty("ownerUserId")
        String ownerUserId,
        @JsonProperty("provider")
        String provider,
        @JsonProperty("alias")
        String alias,
        @JsonProperty("status")
        TeamApiKeyStatus status
) {
}
