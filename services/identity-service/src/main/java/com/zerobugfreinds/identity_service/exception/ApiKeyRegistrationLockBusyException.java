package com.zerobugfreinds.identity_service.exception;

/**
 * Redis 분산 락 획득 실패(동시 등록 경합).
 */
public class ApiKeyRegistrationLockBusyException extends RuntimeException {

	public ApiKeyRegistrationLockBusyException(String message) {
		super(message);
	}
}
