package com.zerobugfreinds.identity_service.dto;

/**
 * Proxy 내부 조회용 API 키 응답.
 */
public record InternalApiKeyResponse(
		String plainKey,
		String keyId
) {
}
