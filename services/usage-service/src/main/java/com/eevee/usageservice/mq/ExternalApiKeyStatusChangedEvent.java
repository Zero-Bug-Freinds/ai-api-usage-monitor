package com.eevee.usageservice.mq;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalApiKeyStatusChangedEvent(
        @JsonProperty("schemaVersion")
        int schemaVersion,

        @JsonProperty("occurredAt")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,

        @JsonProperty("keyId")
        Long keyId,

        @JsonProperty("alias")
        String alias,

        @JsonProperty("userId")
        Long userId,

        @JsonProperty("provider")
        String provider,

        @JsonProperty("status")
        ExternalApiKeyStatus status
) {
}
