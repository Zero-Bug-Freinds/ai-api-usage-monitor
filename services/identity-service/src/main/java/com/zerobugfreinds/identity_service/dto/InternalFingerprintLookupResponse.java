package com.zerobugfreinds.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /internal/v1/api-keys/lookup 성공 응답(문서 v1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
	public static InternalFingerprintLookupResponse personal(
			String userId,
			String keyId,
			String alias,
			String status,
			String keySource
	) {
		return new InternalFingerprintLookupResponse(true, "PERSONAL", userId, null, keyId, alias, status, keySource);
	}

	public static InternalFingerprintLookupResponse team(
			Long teamId,
			String keyId,
			String alias,
			String status,
			String keySource
	) {
		return new InternalFingerprintLookupResponse(true, "TEAM", null, teamId, keyId, alias, status, keySource);
	}
}
