package com.zerobugfreinds.identity.events;

/**
 * 개인 외부 API Key 관련 AMQP 페이로드의 {@code eventType} 상수.
 * team-service {@code TeamEventTypes} 와 동일한 문자열 상수 패턴을 따른다.
 */
public final class IdentityExternalApiKeyEventTypes {

	public static final String EXTERNAL_API_KEY_DELETED = "EXTERNAL_API_KEY_DELETED";
	public static final String EXTERNAL_API_KEY_BUDGET_CHANGED = "EXTERNAL_API_KEY_BUDGET_CHANGED";
	public static final String USER_CONTEXT_CHANGED = "USER_CONTEXT_CHANGED";

	private IdentityExternalApiKeyEventTypes() {
	}
}
