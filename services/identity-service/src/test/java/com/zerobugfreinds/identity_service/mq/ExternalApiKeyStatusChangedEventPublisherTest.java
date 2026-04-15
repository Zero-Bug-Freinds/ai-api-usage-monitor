package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatus;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
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
class ExternalApiKeyStatusChangedEventPublisherTest {

	@Mock
	RabbitTemplate rabbitTemplate;

	@Test
	void publish_sendsJsonPayloadToExchangeAndRoutingKey() throws Exception {
		ExternalApiKeyStatusChangedEventPublisher publisher = new ExternalApiKeyStatusChangedEventPublisher(
				rabbitTemplate,
				"identity.events",
				"identity.external-api-key.status-changed"
		);

		ExternalApiKeyStatusChangedEvent event = ExternalApiKeyStatusChangedEvent.of(
				10L,
				"Main key",
				99L,
				"OPENAI",
				ExternalApiKeyStatus.ACTIVE
		);
		publisher.publish(event);

		ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
				eq("identity.events"),
				eq("identity.external-api-key.status-changed"),
				jsonCaptor.capture()
		);

		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		JsonNode node = mapper.readTree(jsonCaptor.getValue());
		assertThat(node.get("schemaVersion").asInt()).isEqualTo(ExternalApiKeyStatusChangedEvent.CURRENT_SCHEMA_VERSION);
		assertThat(node.get("keyId").asLong()).isEqualTo(10L);
		assertThat(node.get("alias").asText()).isEqualTo("Main key");
		assertThat(node.get("userId").asLong()).isEqualTo(99L);
		assertThat(node.get("provider").asText()).isEqualTo("OPENAI");
		assertThat(node.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(node.hasNonNull("occurredAt")).isTrue();
	}
}
