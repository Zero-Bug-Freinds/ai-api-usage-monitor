package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAccountDeletionEventPublisherTest {

	@Mock
	RabbitTemplate rabbitTemplate;

	@Test
	void publish_sendsJsonPayloadToExchangeAndRoutingKey() throws Exception {
		UserAccountDeletionEventPublisher publisher = new UserAccountDeletionEventPublisher(
				rabbitTemplate,
				"identity.events",
				"identity.user.account-deletion-requested"
		);

		UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(99L, "user@example.com");
		publisher.publish(event);

		ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
				eq("identity.events"),
				eq("identity.user.account-deletion-requested"),
				jsonCaptor.capture()
		);

		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		JsonNode node = mapper.readTree(jsonCaptor.getValue());
		assertThat(node.get("schemaVersion").asInt()).isEqualTo(UserAccountDeletionRequestedEvent.CURRENT_SCHEMA_VERSION);
		assertThat(node.get("identityUserId").asLong()).isEqualTo(99L);
		assertThat(node.get("userEmail").asText()).isEqualTo("user@example.com");
		assertThat(node.hasNonNull("occurredAt")).isTrue();
	}
}
