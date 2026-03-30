package com.zerobugfreinds.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 등록 성공 시 메타데이터만 반환한다. 키 평문·암호문은 포함하지 않는다.
 */
public record ExternalApiKeyRegisterResponse(
		Long id,
		String provider,
		@JsonProperty("alias") String alias,
		Instant createdAt
) {
}
