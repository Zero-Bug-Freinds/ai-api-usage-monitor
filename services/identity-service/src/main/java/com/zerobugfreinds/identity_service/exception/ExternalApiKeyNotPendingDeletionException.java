package com.zerobugfreinds.identity_service.exception;

/**
 * 삭제 예정이 아닌 키에 삭제 취소를 요청한 경우.
 */
public class ExternalApiKeyNotPendingDeletionException extends RuntimeException {

	public ExternalApiKeyNotPendingDeletionException(String message) {
		super(message);
	}
}
