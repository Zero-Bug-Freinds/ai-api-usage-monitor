package com.zerobugfreinds.identity_service.exception;

/**
 * 동일 사용자가 이미 등록한 외부 API 키를 다시 등록하려 할 때.
 */
public class DuplicateExternalApiKeyException extends RuntimeException {

	public DuplicateExternalApiKeyException(String message) {
		super(message);
	}
}
