package com.zerobugfreinds.identity_service.exception;

/**
 * 회원가입 입력 정책 위반 예외.
 */
public class InvalidSignupRequestException extends RuntimeException {

	public InvalidSignupRequestException(String message) {
		super(message);
	}
}
