package com.zerobugfreinds.identity_service.exception;

/**
 * 내부 API 키 역조회 등에 Bearer 내부 토큰이 없거나 잘못된 경우.
 */
public class InternalLookupUnauthorizedException extends RuntimeException {

	public InternalLookupUnauthorizedException(String message) {
		super(message);
	}
}
