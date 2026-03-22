package com.zerobugfreinds.identity_service.exception;

/**
 * 동일 이메일로 가입 시도 시 발생.
 */
public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException(String message) {
		super(message);
	}
}
