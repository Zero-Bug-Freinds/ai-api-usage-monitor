package com.zerobugfreinds.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * agent 등 내부 호출자가 스냅샷 병합용으로 키 ID와 저장 해시만 조회할 때 사용한다. 평문 키는 포함하지 않는다.
 */
public record InternalApiKeyHashEntry(
		@JsonProperty("keyId") Long keyId,
		@JsonProperty("keyHash") String keyHash
) {
}
