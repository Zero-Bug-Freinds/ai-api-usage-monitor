package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.UserAccountDeletionAcknowledgedEvent;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserAccountDeletionAckPublisherTest {

	@Test
	void publish_sendsTeamAckEvent() throws Exception {
		RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		UserAccountDeletionAckPublisher publisher = new UserAccountDeletionAckPublisher(
				rabbitTemplate,
				objectMapper,
				"identity.events",
				"identity.user.account-deletion-ack"
		);
		UserAccountDeletionRequestedEvent requestEvent = UserAccountDeletionRequestedEvent.of(10L, "user@test.com");
		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

		publisher.publish(requestEvent);

		verify(rabbitTemplate).convertAndSend(
				eq("identity.events"),
				eq("identity.user.account-deletion-ack"),
				payloadCaptor.capture()
		);
		UserAccountDeletionAcknowledgedEvent ackEvent = objectMapper.readValue(
				payloadCaptor.getValue(),
				UserAccountDeletionAcknowledgedEvent.class
		);
		assertThat(ackEvent.identityUserId()).isEqualTo(10L);
		assertThat(ackEvent.userEmail()).isEqualTo("user@test.com");
		assertThat(ackEvent.source()).isEqualTo(UserAccountDeletionAcknowledgedEvent.SOURCE_TEAM);
	}
}
