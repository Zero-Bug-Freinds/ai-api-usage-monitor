package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyBudgetChangedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import com.zerobugfreinds.identity.events.UserContextChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes identity external API key messages to RabbitMQ only after transaction commit.
 * <ul>
 *     <li>{@link ExternalApiKeyStatusChangedEvent} — 등록·수정·삭제 예약·취소 등 상태 동기화</li>
 *     <li>{@link ExternalApiKeyDeletedEvent} — 물리 삭제(즉시 또는 유예 만료), usage 로그 보존 여부 포함</li>
 * </ul>
 */
@Component
public class ExternalApiKeyStatusChangedEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyStatusChangedEventPublisher.class);
	private static final ObjectMapper EVENT_JSON = new ObjectMapper().registerModule(new JavaTimeModule());

	private final RabbitTemplate rabbitTemplate;
	private final String exchange;
	private final String routingKey;
	private final String userContextRoutingKey;

	public ExternalApiKeyStatusChangedEventPublisher(
			RabbitTemplate rabbitTemplate,
			@Value("${identity.external-api-key-event.exchange:identity.events}") String exchange,
			@Value("${identity.external-api-key-event.routing-key:identity.external-api-key.status-changed}") String routingKey,
			@Value("${identity.user-context-event.routing-key:identity.user.context-changed}") String userContextRoutingKey
	) {
		this.rabbitTemplate = rabbitTemplate;
		this.exchange = exchange;
		this.routingKey = routingKey;
		this.userContextRoutingKey = userContextRoutingKey;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onExternalApiKeyStatusChanged(ExternalApiKeyStatusChangedEvent event) {
		publishJson(event, "ExternalApiKeyStatusChangedEvent", event.keyId(), event.userId(), event.status());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onExternalApiKeyDeleted(ExternalApiKeyDeletedEvent event) {
		publishJson(
				event,
				"ExternalApiKeyDeletedEvent",
				event.apiKeyId(),
				event.userId(),
				event.retainLogs()
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onExternalApiKeyBudgetChanged(ExternalApiKeyBudgetChangedEvent event) {
		publishJson(
				event,
				"ExternalApiKeyBudgetChangedEvent",
				event.keyId(),
				event.userId(),
				event.monthlyBudgetUsd()
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onUserContextChanged(UserContextChangedEvent event) {
		publishJson(
				event,
				"UserContextChangedEvent",
				event.userId(),
				event.userId(),
				event.activeTeamId(),
				userContextRoutingKey
		);
	}

	private void publishJson(Object payload, String label, Object apiKeyOrKeyId, Long userId, Object statusOrRetain) {
		publishJson(payload, label, apiKeyOrKeyId, userId, statusOrRetain, routingKey);
	}

	private void publishJson(
			Object payload,
			String label,
			Object apiKeyOrKeyId,
			Long userId,
			Object statusOrRetain,
			String targetRoutingKey
	) {
		try {
			String json = EVENT_JSON.writeValueAsString(payload);
			rabbitTemplate.convertAndSend(exchange, targetRoutingKey, json);
			log.info(
					"Published {} apiKeyIdOrKeyId={} userId={} detail={} exchange={} routingKey={}",
					label,
					apiKeyOrKeyId,
					userId,
					statusOrRetain,
					exchange,
					targetRoutingKey
			);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(label + " serialization failed", e);
		}
	}
}
