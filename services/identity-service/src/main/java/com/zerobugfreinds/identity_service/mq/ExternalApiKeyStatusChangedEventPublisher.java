package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes external API key status events to RabbitMQ only after transaction commit.
 */
@Component
public class ExternalApiKeyStatusChangedEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyStatusChangedEventPublisher.class);
	private static final ObjectMapper EVENT_JSON = new ObjectMapper().registerModule(new JavaTimeModule());

	private final RabbitTemplate rabbitTemplate;
	private final String exchange;
	private final String routingKey;

	public ExternalApiKeyStatusChangedEventPublisher(
			RabbitTemplate rabbitTemplate,
			@Value("${identity.external-api-key-event.exchange:identity.events}") String exchange,
			@Value("${identity.external-api-key-event.routing-key:identity.external-api-key.status-changed}") String routingKey
	) {
		this.rabbitTemplate = rabbitTemplate;
		this.exchange = exchange;
		this.routingKey = routingKey;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onExternalApiKeyStatusChanged(ExternalApiKeyStatusChangedEvent event) {
		publish(event);
	}

	void publish(ExternalApiKeyStatusChangedEvent event) {
		try {
			String json = EVENT_JSON.writeValueAsString(event);
			rabbitTemplate.convertAndSend(exchange, routingKey, json);
			log.info(
					"Published ExternalApiKeyStatusChangedEvent keyId={} userId={} status={} exchange={} routingKey={}",
					event.keyId(),
					event.userId(),
					event.status(),
					exchange,
					routingKey
			);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("ExternalApiKeyStatusChangedEvent serialization failed", e);
		}
	}
}
