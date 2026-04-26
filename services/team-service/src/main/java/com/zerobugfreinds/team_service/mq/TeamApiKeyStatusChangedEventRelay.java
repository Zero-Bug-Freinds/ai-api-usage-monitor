package com.zerobugfreinds.team_service.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.team_service.config.TeamApiKeyStatusEventRabbitConstants;
import com.zerobugfreinds.team_service.entity.TeamEventOutbox;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamApiKeyStatusChangedEvent;
import com.zerobugfreinds.team_service.repository.TeamEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션 커밋 후 TeamApiKeyStatusChangedEvent를 Outbox에 저장하고 RabbitMQ로 릴레이한다.
 */
@Component
public class TeamApiKeyStatusChangedEventRelay {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyStatusChangedEventRelay.class);

    private final TeamEventOutboxRepository teamEventOutboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;

    public TeamApiKeyStatusChangedEventRelay(
            TeamEventOutboxRepository teamEventOutboxRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${" + TeamApiKeyStatusEventRabbitConstants.EXCHANGE_PROPERTY + ":"
                    + TeamApiKeyStatusEventRabbitConstants.DEFAULT_EXCHANGE + "}") String exchange,
            @Value("${" + TeamApiKeyStatusEventRabbitConstants.ROUTING_KEY_PROPERTY + ":"
                    + TeamApiKeyStatusEventRabbitConstants.DEFAULT_ROUTING_KEY + "}") String routingKey
    ) {
        this.teamEventOutboxRepository = teamEventOutboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamApiKeyStatusChanged(TeamApiKeyStatusChangedEvent event) {
        if (teamEventOutboxRepository.existsByEventId(event.eventId())) {
            return;
        }

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize TeamApiKeyStatusChangedEvent eventId={}", event.eventId(), ex);
            return;
        }

        TeamEventOutbox outbox = TeamEventOutbox.create(
                event.eventId(),
                event.teamApiKeyId(),
                event.eventType(),
                payload
        );
        outbox = teamEventOutboxRepository.save(outbox);

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            outbox.markPublished();
            teamEventOutboxRepository.save(outbox);
        } catch (Exception ex) {
            log.error(
                    "Failed to publish TeamApiKeyStatusChangedEvent eventId={} exchange={} routingKey={}",
                    event.eventId(),
                    exchange,
                    routingKey,
                    ex
            );
        }
    }
}
