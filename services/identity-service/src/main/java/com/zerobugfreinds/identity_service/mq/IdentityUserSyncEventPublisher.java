package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.IdentityUserSyncEvent;
import com.zerobugfreinds.identity_service.config.RabbitOutboundAsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * team-service 가 {@code identity_user_sync} 를 채우기 위해 구독하는 사용자 동기화 메시지를 발행한다.
 */
@Component
public class IdentityUserSyncEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IdentityUserSyncEventPublisher.class);
    private static final ObjectMapper EVENT_JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ApplicationEventPublisher applicationEventPublisher;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public IdentityUserSyncEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            RabbitTemplate rabbitTemplate,
            @Value("${identity.user-sync.exchange:identity.events}") String exchange,
            @Value("${identity.user-sync.routing-key:identity.user.sync}") String routingKey
    ) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    /**
     * 트랜잭션 커밋 후에 RabbitMQ 로 전송한다.
     */
    public void publishAfterCommit(IdentityUserSyncEvent event) {
        applicationEventPublisher.publishEvent(new IdentityUserSyncCommitted(event));
    }

    /**
     * 관리용 재발행·테스트 등 즉시 발행이 필요할 때 사용한다.
     */
    public void publishImmediately(IdentityUserSyncEvent event) {
        sendJson(event);
    }

    @Async(RabbitOutboundAsyncConfig.RABBIT_TRANSACTIONAL_OUTBOUND_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommitted(IdentityUserSyncCommitted wrapped) {
        sendJson(wrapped.event());
    }

    private void sendJson(IdentityUserSyncEvent event) {
        try {
            String json = EVENT_JSON.writeValueAsString(event);
            rabbitTemplate.convertAndSend(exchange, routingKey, json);
            log.info(
                    "Published IdentityUserSyncEvent userId={} eventType={} exchange={} routingKey={}",
                    event.userId(),
                    event.eventType(),
                    exchange,
                    routingKey
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("IdentityUserSyncEvent serialization failed", e);
        }
    }
}
