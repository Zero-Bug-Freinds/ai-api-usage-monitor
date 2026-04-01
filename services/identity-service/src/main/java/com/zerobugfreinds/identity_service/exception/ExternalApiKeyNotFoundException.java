package com.zerobugfreinds.identity_service.exception;

/**
 * 사용자/제공자 기준 외부 API 키를 찾을 수 없을 때 발생한다.
 */
public class ExternalApiKeyNotFoundException extends RuntimeException {

	public ExternalApiKeyNotFoundException(String message) {
		super(message);
	}
}
