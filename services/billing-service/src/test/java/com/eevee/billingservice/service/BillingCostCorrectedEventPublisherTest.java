package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.events.BillingCostCorrectedEvent;
import com.eevee.billingservice.events.BillingCostCorrectionAmqp;
import com.eevee.usage.events.AiProvider;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BillingCostCorrectedEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private BillingCostCorrectedEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        BillingRabbitProperties props = new BillingRabbitProperties();
        props.getCorrectionOut().setEnabled(true);
        props.getCorrectionOut().setExchange("billing.events");
        props.getCorrectionOut().setRoutingKey("billing.cost.corrected");
        publisher = new BillingCostCorrectedEventPublisher(rabbitTemplate, props, objectMapper);
    }

    @Test
    void sendsBillingCostCorrectedEvent() throws Exception {
        UUID correctionId = UUID.randomUUID();
        BillingCostCorrectionAmqp applied = new BillingCostCorrectionAmqp(
                BillingCostCorrectionAmqp.CURRENT_SCHEMA_VERSION,
                correctionId,
                "u",
                "k",
                LocalDate.of(2025, 6, 1),
                new BigDecimal("-0.01"),
                LocalDate.of(2025, 6, 10),
                AiProvider.OPENAI,
                "gpt-4o-mini",
                0L,
                0L,
                new BigDecimal("9.99"),
                UUID.randomUUID()
        );

        Instant occurredAt = Instant.parse("2025-06-11T00:00:00Z");
        publisher.publish(applied, occurredAt);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("billing.events"), eq("billing.cost.corrected"), captor.capture());
        Message sentMessage = captor.getValue();
        BillingCostCorrectedEvent sent = objectMapper.readValue(
                new String(sentMessage.getBody(), StandardCharsets.UTF_8),
                BillingCostCorrectedEvent.class);
        assertThat(sent.correctionEventId()).isEqualTo(correctionId);
        assertThat(sent.appliedDeltaCostUsd()).isEqualByComparingTo("-0.01");
        assertThat(sent.schemaVersion()).isEqualTo(BillingCostCorrectedEvent.CURRENT_SCHEMA_VERSION);
        assertThat(sent.occurredAt()).isEqualTo(occurredAt);
        assertThat(sentMessage.getMessageProperties().getHeaders().get("userId")).isEqualTo("u");
        assertThat(sentMessage.getMessageProperties().getHeaders().get("apiKeyId")).isEqualTo("k");
    }
}
