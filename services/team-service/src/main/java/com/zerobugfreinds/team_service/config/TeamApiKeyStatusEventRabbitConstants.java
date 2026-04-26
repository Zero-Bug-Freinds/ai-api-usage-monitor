package com.zerobugfreinds.team_service.config;

/**
 * Team API Key 상태 동기화 이벤트 라우팅 상수.
 * External API Key 이벤트와 충돌하지 않도록 별도 네임스페이스를 사용한다.
 */
public final class TeamApiKeyStatusEventRabbitConstants {

    public static final String EXCHANGE_PROPERTY = "team.api-key-status-event.exchange";
    public static final String ROUTING_KEY_PROPERTY = "team.api-key-status-event.routing-key";
    public static final String QUEUE_PROPERTY = "team.api-key-status-event.queue";

    public static final String DEFAULT_EXCHANGE = "team.api-key.exchange";
    public static final String DEFAULT_ROUTING_KEY = "team.api-key.status.changed";
    public static final String DEFAULT_QUEUE = "team.api-key.status.changed.queue";

    private TeamApiKeyStatusEventRabbitConstants() {
    }
}
