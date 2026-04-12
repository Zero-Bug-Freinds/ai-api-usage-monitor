package com.zerobugfreinds.identity_service.domain;

/**
 * 외부 API 키 삭제 예정 유예 기간(일). 기본값과 허용 범위.
 */
public final class ExternalApiKeyDeletionPolicy {

	public static final int DEFAULT_GRACE_DAYS = 7;
	public static final int MIN_GRACE_DAYS = 1;
	public static final int MAX_GRACE_DAYS = 365;

	private ExternalApiKeyDeletionPolicy() {
	}
}
