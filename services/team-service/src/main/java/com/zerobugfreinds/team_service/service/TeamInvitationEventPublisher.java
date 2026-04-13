package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.team_service.event.TeamMemberInvitedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TeamInvitationEventPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final String exchange;
	private final String routingKey;

	public TeamInvitationEventPublisher(
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

	public void publish(TeamMemberInvitedEvent payload) {
		try {
			String json = objectMapper.writeValueAsString(payload);
			rabbitTemplate.convertAndSend(exchange, routingKey, json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("team-member-invited event serialization failed", e);
		}
	}
}
