package com.zerobugfreinds.identity_service.exception;

/**
 * 삭제 예정(유예 중)인 키를 수정하려 한 경우.
 */
public class ExternalApiKeyPendingDeletionException extends RuntimeException {

	public ExternalApiKeyPendingDeletionException(String message) {
		super(message);
	}
}
