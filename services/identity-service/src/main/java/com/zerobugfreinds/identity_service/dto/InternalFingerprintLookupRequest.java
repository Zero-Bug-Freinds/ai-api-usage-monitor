package com.zerobugfreinds.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /internal/v1/api-keys/lookup 요청 본문.
 */
public record InternalFingerprintLookupRequest(
		@JsonProperty("fingerprint") String fingerprint,
		@JsonProperty("provider") String provider
) {
}
