package com.eevee.usageservice.mq;

/**
 * identity-service AMQP 페이로드의 {@code eventType} 값 (identity-events 와 동일 문자열).
 */
public final class IdentityExternalApiKeyEventTypes {

    public static final String EXTERNAL_API_KEY_DELETED = "EXTERNAL_API_KEY_DELETED";

    private IdentityExternalApiKeyEventTypes() {
    }
}
