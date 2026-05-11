package com.eevee.usageservice.mq;

/**
 * identity-service AMQP 페이로드의 {@code eventType} 값 (identity-events 와 동일 문자열).
 */
public final class IdentityExternalApiKeyEventTypes {

    public static final String EXTERNAL_API_KEY_DELETED = "EXTERNAL_API_KEY_DELETED";

    public static final String EXTERNAL_API_KEY_BUDGET_CHANGED = "EXTERNAL_API_KEY_BUDGET_CHANGED";

    public static final String USER_CONTEXT_CHANGED = "USER_CONTEXT_CHANGED";

    private IdentityExternalApiKeyEventTypes() {
    }
}
