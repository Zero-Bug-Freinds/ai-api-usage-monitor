package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usage.events.UsageRecordedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageCostFinalizedEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private UsageCostFinalizedEventPublisher publisher;

    @BeforeEach
    void setUp() {
        BillingRabbitProperties props = new BillingRabbitProperties();
        props.getCostOut().setEnabled(true);
        props.getCostOut().setExchange("billing.events");
        props.getCostOut().setRoutingKey("usage.cost.finalized");
        publisher = new UsageCostFinalizedEventPublisher(rabbitTemplate, props);
    }

    @Test
    void sendsUsageCostFinalizedEventToDedicatedExchange() {
        UsageRecordedEvent source = new UsageRecordedEvent(
                java.util.UUID.randomUUID(),
                Instant.parse("2025-06-01T12:00:00Z"),
                "c",
                "u",
                null,
                null,
                "k",
                "hash",
                "managed",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 1L, 2L, 3L),
                BigDecimal.ZERO,
                "/p",
                "h",
                false,
                true,
                200
        );

        publisher.publish(source, "gpt-4o-mini", new BigDecimal("0.01"));

        ArgumentCaptor<UsageCostFinalizedEvent> captor = ArgumentCaptor.forClass(UsageCostFinalizedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("billing.events"), eq("usage.cost.finalized"), captor.capture());
        UsageCostFinalizedEvent sent = captor.getValue();
        assertThat(sent.eventId()).isEqualTo(source.eventId());
        assertThat(sent.estimatedCostUsd()).isEqualByComparingTo("0.01");
        assertThat(sent.schemaVersion()).isEqualTo(UsageCostFinalizedEvent.CURRENT_SCHEMA_VERSION);
        assertThat(sent.provider()).isEqualTo(AiProvider.OPENAI);
        assertThat(sent.model()).isEqualTo("gpt-4o-mini");
    }
}
