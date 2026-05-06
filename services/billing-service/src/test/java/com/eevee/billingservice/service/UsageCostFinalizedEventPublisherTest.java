package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageCostFinalizedEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private UsageCostFinalizedEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        BillingRabbitProperties props = new BillingRabbitProperties();
        props.getCostOut().setEnabled(true);
        props.getCostOut().setExchange("billing.events");
        props.getCostOut().setRoutingKey("usage.cost.finalized");
        publisher = new UsageCostFinalizedEventPublisher(rabbitTemplate, props, objectMapper);
    }

    @Test
    void sendsUsageCostFinalizedEventToDedicatedExchange() throws Exception {
        UsageRecordedEvent source = new UsageRecordedEvent(
                java.util.UUID.randomUUID(),
                Instant.parse("2025-06-01T12:00:00Z"),
                "c",
                "u",
                null,
                "t",
                "k",
                null,
                "hash",
                "managed",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 1L, 2L, 3L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/p",
                "h",
                false,
                true,
                200
        );

        publisher.publish(source, "gpt-4o-mini", new BigDecimal("0.01"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("billing.events"), eq("usage.cost.finalized"), captor.capture());
        Message sentMessage = captor.getValue();
        UsageCostFinalizedEvent sent = objectMapper.readValue(
                new String(sentMessage.getBody(), StandardCharsets.UTF_8),
                UsageCostFinalizedEvent.class);
        assertThat(sent.eventId()).isEqualTo(source.eventId());
        assertThat(sent.estimatedCostUsd()).isEqualByComparingTo("0.01");
        assertThat(sent.schemaVersion()).isEqualTo(UsageCostFinalizedEvent.CURRENT_SCHEMA_VERSION);
        assertThat(sent.provider()).isEqualTo(AiProvider.OPENAI);
        assertThat(sent.model()).isEqualTo("gpt-4o-mini");

        assertThat(sentMessage.getMessageProperties().getHeaders().get("subjectType")).isEqualTo("API_KEY");
        assertThat(sentMessage.getMessageProperties().getHeaders().get("userId")).isEqualTo("u");
        assertThat(sentMessage.getMessageProperties().getHeaders().get("teamId")).isEqualTo("t");
        assertThat(sentMessage.getMessageProperties().getHeaders().get("apiKeyId")).isEqualTo("k");
    }
}
