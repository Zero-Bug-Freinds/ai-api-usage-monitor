package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.events.BillingBudgetThresholdReachedEvent;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BudgetThresholdEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private BudgetThresholdEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        BillingRabbitProperties props = new BillingRabbitProperties();
        props.getBudgetOut().setEnabled(true);
        props.getBudgetOut().setExchange("billing.events");
        props.getBudgetOut().setRoutingKey("billing.budget.threshold.reached");
        publisher = new BudgetThresholdEventPublisher(rabbitTemplate, props, objectMapper);
    }

    @Test
    void publishesOnceWhenCrossingEightyPercent() throws Exception {
        publisher.publishIfCrossed(
                "u",
                "t",
                "k",
                LocalDate.parse("2026-04-01"),
                new BigDecimal("70.00"),
                new BigDecimal("80.00"),
                new BigDecimal("100.00")
        );

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("billing.events"), eq("billing.budget.threshold.reached"), captor.capture());
        Message msg = captor.getValue();
        BillingBudgetThresholdReachedEvent sent = objectMapper.readValue(
                new String(msg.getBody(), StandardCharsets.UTF_8),
                BillingBudgetThresholdReachedEvent.class);

        assertThat(sent.thresholdPct()).isEqualByComparingTo("0.8");
        assertThat(sent.monthStart()).isEqualTo(LocalDate.parse("2026-04-01"));
        assertThat(sent.monthlyTotalUsd()).isEqualByComparingTo("80.00");
        assertThat(sent.monthlyBudgetUsd()).isEqualByComparingTo("100.00");

        assertThat(msg.getMessageProperties().getHeaders().get("subjectType")).isEqualTo("API_KEY");
        assertThat(msg.getMessageProperties().getHeaders().get("userId")).isEqualTo("u");
        assertThat(msg.getMessageProperties().getHeaders().get("teamId")).isEqualTo("t");
        assertThat(msg.getMessageProperties().getHeaders().get("apiKeyId")).isEqualTo("k");
    }

    @Test
    void publishesForAllThresholdsCrossedInOneStep() throws Exception {
        publisher.publishIfCrossed(
                "u",
                null,
                "k",
                LocalDate.parse("2026-04-01"),
                new BigDecimal("79.00"),
                new BigDecimal("101.00"),
                new BigDecimal("100.00")
        );

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, times(3)).send(eq("billing.events"), eq("billing.budget.threshold.reached"), captor.capture());

        List<BigDecimal> thresholds = captor.getAllValues()
                .stream()
                .map(m -> {
                    try {
                        BillingBudgetThresholdReachedEvent e = objectMapper.readValue(
                                new String(m.getBody(), StandardCharsets.UTF_8),
                                BillingBudgetThresholdReachedEvent.class);
                        return e.thresholdPct();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .toList();

        assertThat(thresholds).containsExactlyInAnyOrder(
                new BigDecimal("0.8"),
                new BigDecimal("0.9"),
                BigDecimal.ONE
        );
    }
}

