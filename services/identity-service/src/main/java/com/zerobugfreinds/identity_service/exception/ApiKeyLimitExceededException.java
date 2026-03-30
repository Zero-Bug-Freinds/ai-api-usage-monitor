package com.zerobugfreinds.identity_service.exception;

/**
 * 사용자당 API 키 개수 상한을 초과했을 때 발생하는 예외.
 */
public class ApiKeyLimitExceededException extends RuntimeException {

	public ApiKeyLimitExceededException(String message) {
		super(message);
	}
}
