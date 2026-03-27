package com.zerobugfreinds.identity_service.exception;

/**
 * 로그인 인증 실패 예외.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException(String message) {
		super(message);
	}
}
