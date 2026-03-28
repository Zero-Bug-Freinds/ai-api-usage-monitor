package com.zerobugfreinds.identity_service.exception;

/**
 * 인증 계약 위반(예: 토큰 타입 불일치) 예외.
 */
public class AuthContractViolationException extends RuntimeException {

	public AuthContractViolationException(String message) {
		super(message);
	}
}
