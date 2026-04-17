package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 팀 도메인 이벤트를 RabbitMQ TopicExchange 로 발행한다. 본문 JSON과 AMQP 헤더 {@code eventType}에 동일 식별자를 넣는다.
 */
@Component
public class TeamDomainEventPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final String exchange;
	private final String routingKey;

	public TeamDomainEventPublisher(
			RabbitTemplate rabbitTemplate,
			ObjectMapper objectMapper,
			@Value("${team.member-added-event.exchange:team.events}") String exchange,
			@Value("${team.member-added-event.routing-key:team-member-added}") String routingKey
	) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.exchange = exchange;
		this.routingKey = routingKey;
	}

	public void publish(TeamDomainOutboundEvent event) {
		try {
			String json = objectMapper.writeValueAsString(event);
			MessageProperties props = new MessageProperties();
			props.setHeader("eventType", event.eventType());
			props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
			Message message = new Message(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), props);
			rabbitTemplate.send(exchange, routingKey, message);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("team domain event serialization failed: " + event.eventType(), e);
		}
	}
}
