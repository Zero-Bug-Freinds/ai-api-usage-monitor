package com.eevee.proxyservice.key.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InternalFingerprintLookupResponse(
        @JsonProperty("found") boolean found,
        @JsonProperty("ownerType") String ownerType,
        @JsonProperty("userId") String userId,
        @JsonProperty("teamId") Long teamId,
        @JsonProperty("keyId") String keyId,
        @JsonProperty("alias") String alias,
        @JsonProperty("status") String status,
        @JsonProperty("keySource") String keySource
) {
}
