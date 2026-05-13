package com.eevee.usageservice.mq;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamApiKeyDeletionScheduledEvent(
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
        String actorUserId,
        @JsonProperty("teamName")
        String teamName,
        @JsonProperty("recipientUserIds")
        List<String> recipientUserIds
) {
}
