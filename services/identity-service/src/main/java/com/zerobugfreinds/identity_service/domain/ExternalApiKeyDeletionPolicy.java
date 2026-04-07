package com.zerobugfreinds.identity_service.domain;

import java.time.Duration;

/**
 * 삭제 요청 후 완전 삭제까지 유예 기간. 운영에서 바꾸려면 설정 프로퍼티로 승격한다.
 */
public final class ExternalApiKeyDeletionPolicy {

	public static final Duration PENDING_RETENTION = Duration.ofDays(7);

	private ExternalApiKeyDeletionPolicy() {
	}
}
