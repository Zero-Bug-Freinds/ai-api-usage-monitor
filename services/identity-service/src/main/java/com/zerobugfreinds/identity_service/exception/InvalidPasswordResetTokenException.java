package com.zerobugfreinds.identity_service.exception;

/**
 * 재설정 토큰이 없거나 만료·사용됨 등으로 유효하지 않을 때.
 */
public class InvalidPasswordResetTokenException extends RuntimeException {

	public InvalidPasswordResetTokenException(String message) {
		super(message);
	}
}
