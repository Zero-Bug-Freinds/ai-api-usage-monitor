package com.zerobugfreinds.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 등록 성공 시 메타데이터만 반환한다. 키 평문·암호문은 포함하지 않는다.
 * 삭제 예정이면 {@code deletionRequestedAt}, {@code permanentDeletionAt}이 채워진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExternalApiKeyRegisterResponse(
		Long id,
		String provider,
		@JsonProperty("alias") String alias,
		Instant createdAt,
		Instant deletionRequestedAt,
		Instant permanentDeletionAt
) {
	public ExternalApiKeyRegisterResponse(Long id, String provider, String alias, Instant createdAt) {
		this(id, provider, alias, createdAt, null, null);
	}
}
