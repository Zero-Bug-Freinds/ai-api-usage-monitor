package com.zerobugfreinds.team_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InternalFingerprintLookupRequest(
        @JsonProperty("fingerprint") String fingerprint,
        @JsonProperty("provider") String provider
) {
}
