package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.UserAccountDeletionAcknowledgedEvent;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UserAccountDeletionAckPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final String exchange;
	private final String routingKey;

	public UserAccountDeletionAckPublisher(
			RabbitTemplate rabbitTemplate,
			ObjectMapper objectMapper,
			@Value("${identity.account-deletion-ack.exchange:identity.events}") String exchange,
			@Value("${identity.account-deletion-ack.routing-key:identity.user.account-deletion-ack}") String routingKey
	) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.exchange = exchange;
		this.routingKey = routingKey;
	}

	public void publish(UserAccountDeletionRequestedEvent requestEvent) {
		UserAccountDeletionAcknowledgedEvent ackEvent = new UserAccountDeletionAcknowledgedEvent(
				UserAccountDeletionAcknowledgedEvent.CURRENT_SCHEMA_VERSION,
				Instant.now(),
				requestEvent.identityUserId(),
				requestEvent.userEmail(),
				UserAccountDeletionAcknowledgedEvent.SOURCE_TEAM
		);
		try {
			String json = objectMapper.writeValueAsString(ackEvent);
			rabbitTemplate.convertAndSend(exchange, routingKey, json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("user-account-deletion ack event serialization failed", e);
		}
	}
}
