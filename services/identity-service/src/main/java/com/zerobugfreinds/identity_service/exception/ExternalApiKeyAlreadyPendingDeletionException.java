package com.zerobugfreinds.identity_service.exception;

/**
 * 이미 삭제 예정(유예 중)인 키에 삭제를 다시 요청한 경우.
 */
public class ExternalApiKeyAlreadyPendingDeletionException extends RuntimeException {

	public ExternalApiKeyAlreadyPendingDeletionException(String message) {
		super(message);
	}
}
