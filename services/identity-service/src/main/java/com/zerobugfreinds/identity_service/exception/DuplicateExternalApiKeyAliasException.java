package com.zerobugfreinds.identity_service.exception;

/**
 * 동일 사용자의 외부 API 키 별칭이 중복될 때 발생한다.
 */
public class DuplicateExternalApiKeyAliasException extends RuntimeException {

	public DuplicateExternalApiKeyAliasException(String message) {
		super(message);
	}
}
