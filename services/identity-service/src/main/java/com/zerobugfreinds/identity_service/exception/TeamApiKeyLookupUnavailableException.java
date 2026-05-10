package com.zerobugfreinds.identity_service.exception;

/**
 * 팀 API 키 중복 검증을 위해 team-service 내부 조회를 호출할 수 없을 때.
 */
public class TeamApiKeyLookupUnavailableException extends RuntimeException {

	public TeamApiKeyLookupUnavailableException(String message) {
		super(message);
	}

	public TeamApiKeyLookupUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
