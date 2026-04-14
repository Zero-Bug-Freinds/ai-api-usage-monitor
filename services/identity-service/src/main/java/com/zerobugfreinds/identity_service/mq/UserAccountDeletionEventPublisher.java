package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * usage / billing / team 등이 구독할 회원 탈퇴(데이터 삭제 요청) 이벤트를 발행한다.
 */
@Component
public class UserAccountDeletionEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionEventPublisher.class);

	private static final ObjectMapper EVENT_JSON = new ObjectMapper().registerModule(new JavaTimeModule());

	private final RabbitTemplate rabbitTemplate;
	private final String exchange;
	private final String routingKey;

	public UserAccountDeletionEventPublisher(
			RabbitTemplate rabbitTemplate,
			@Value("${identity.account-deletion-event.exchange:identity.events}") String exchange,
			@Value("${identity.account-deletion-event.routing-key:identity.user.account-deletion-requested}") String routingKey
	) {
		this.rabbitTemplate = rabbitTemplate;
		this.exchange = exchange;
		this.routingKey = routingKey;
	}

	public void publish(UserAccountDeletionRequestedEvent event) {
		try {
			String json = EVENT_JSON.writeValueAsString(event);
			rabbitTemplate.convertAndSend(exchange, routingKey, json);
			log.info(
					"Published UserAccountDeletionRequestedEvent identityUserId={} exchange={} routingKey={}",
					event.identityUserId(),
					exchange,
					routingKey
			);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("UserAccountDeletionRequestedEvent serialization failed", e);
		}
	}
}
