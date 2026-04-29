package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyBudgetChangedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatus;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import com.zerobugfreinds.identity.events.IdentityExternalApiKeyEventTypes;

import java.math.BigDecimal;
import java.time.Instant;

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
		publisher.onExternalApiKeyStatusChanged(event);

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

	@Test
	void publish_deleted_sendsTeamStylePayload() throws Exception {
		ExternalApiKeyStatusChangedEventPublisher publisher = new ExternalApiKeyStatusChangedEventPublisher(
				rabbitTemplate,
				"identity.events",
				"identity.external-api-key.status-changed"
		);

		ExternalApiKeyDeletedEvent deleted = ExternalApiKeyDeletedEvent.of(
				99L,
				10L,
				Instant.parse("2026-04-15T12:00:00Z"),
				false,
				"OPENAI",
				"Main key"
		);
		publisher.onExternalApiKeyDeleted(deleted);

		ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
				eq("identity.events"),
				eq("identity.external-api-key.status-changed"),
				jsonCaptor.capture()
		);

		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		JsonNode node = mapper.readTree(jsonCaptor.getValue());
		assertThat(node.get("eventType").asText()).isEqualTo(IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED);
		assertThat(node.get("userId").asLong()).isEqualTo(99L);
		assertThat(node.get("apiKeyId").asLong()).isEqualTo(10L);
		assertThat(node.get("retainLogs").asBoolean()).isFalse();
		assertThat(node.hasNonNull("occurredAt")).isTrue();
		assertThat(node.get("provider").asText()).isEqualTo("OPENAI");
		assertThat(node.get("alias").asText()).isEqualTo("Main key");
	}

	@Test
	void publish_budgetChanged_sendsBudgetPayload() throws Exception {
		ExternalApiKeyStatusChangedEventPublisher publisher = new ExternalApiKeyStatusChangedEventPublisher(
				rabbitTemplate,
				"identity.events",
				"identity.external-api-key.status-changed"
		);

		ExternalApiKeyBudgetChangedEvent event = ExternalApiKeyBudgetChangedEvent.of(
				10L,
				"Main key",
				99L,
				"OPENAI",
				ExternalApiKeyStatus.ACTIVE,
				new BigDecimal("123.45")
		);
		publisher.onExternalApiKeyBudgetChanged(event);

		ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
				eq("identity.events"),
				eq("identity.external-api-key.status-changed"),
				jsonCaptor.capture()
		);

		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		JsonNode node = mapper.readTree(jsonCaptor.getValue());
		assertThat(node.get("eventType").asText()).isEqualTo(IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_BUDGET_CHANGED);
		assertThat(node.get("schemaVersion").asInt()).isEqualTo(ExternalApiKeyBudgetChangedEvent.CURRENT_SCHEMA_VERSION);
		assertThat(node.get("keyId").asLong()).isEqualTo(10L);
		assertThat(node.get("userId").asLong()).isEqualTo(99L);
		assertThat(node.get("monthlyBudgetUsd").asText()).isEqualTo("123.45");
		assertThat(node.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(node.hasNonNull("occurredAt")).isTrue();
	}
}
