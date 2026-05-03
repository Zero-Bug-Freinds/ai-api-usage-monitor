package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.events.BillingBudgetThresholdReachedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class BudgetThresholdEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BudgetThresholdEventPublisher.class);

    /** Monthly spend / budget ratios at which one notification is emitted when first crossed (10% steps). */
    private static final List<BigDecimal> DEFAULT_THRESHOLDS = List.of(
            new BigDecimal("0.1"),
            new BigDecimal("0.2"),
            new BigDecimal("0.3"),
            new BigDecimal("0.4"),
            new BigDecimal("0.5"),
            new BigDecimal("0.6"),
            new BigDecimal("0.7"),
            new BigDecimal("0.8"),
            new BigDecimal("0.9"),
            BigDecimal.ONE
    );

    private final RabbitTemplate rabbitTemplate;
    private final BillingRabbitProperties rabbitProperties;
    private final ObjectMapper objectMapper;

    public BudgetThresholdEventPublisher(
            RabbitTemplate rabbitTemplate,
            BillingRabbitProperties rabbitProperties,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitProperties = rabbitProperties;
        this.objectMapper = objectMapper;
    }

    public void publishIfCrossed(
            String userId,
            String teamId,
            String apiKeyId,
            LocalDate monthStart,
            BigDecimal monthlyTotalUsdBefore,
            BigDecimal monthlyTotalUsdAfter,
            BigDecimal monthlyBudgetUsd
    ) {
        BillingRabbitProperties.BudgetOut out = rabbitProperties.getBudgetOut();
        if (!out.isEnabled()) {
            return;
        }
        if (monthStart == null || monthlyBudgetUsd == null || monthlyBudgetUsd.signum() <= 0) {
            return;
        }
        if (monthlyTotalUsdBefore == null || monthlyTotalUsdAfter == null) {
            return;
        }
        if (monthlyTotalUsdAfter.compareTo(monthlyTotalUsdBefore) < 0) {
            return;
        }

        for (BigDecimal threshold : DEFAULT_THRESHOLDS) {
            if (!isCrossed(monthlyTotalUsdBefore, monthlyTotalUsdAfter, monthlyBudgetUsd, threshold)) {
                continue;
            }
            publishOne(out, userId, teamId, apiKeyId, monthStart, threshold, monthlyTotalUsdAfter, monthlyBudgetUsd);
        }
    }

    private void publishOne(
            BillingRabbitProperties.BudgetOut out,
            String userId,
            String teamId,
            String apiKeyId,
            LocalDate monthStart,
            BigDecimal thresholdPct,
            BigDecimal monthlyTotalUsd,
            BigDecimal monthlyBudgetUsd
    ) {
        BillingBudgetThresholdReachedEvent payload = new BillingBudgetThresholdReachedEvent(
                BillingBudgetThresholdReachedEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                monthStart,
                thresholdPct,
                monthlyTotalUsd,
                monthlyBudgetUsd
        );

        log.debug(
                "Publishing BillingBudgetThresholdReachedEvent monthStart={} thresholdPct={} exchange={} routingKey={}",
                monthStart,
                thresholdPct,
                out.getExchange(),
                out.getRoutingKey());

        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding(StandardCharsets.UTF_8.name());
            props.setHeader("subjectType", resolveSubjectType(userId, teamId, apiKeyId));
            if (hasText(userId)) {
                props.setHeader("userId", userId);
            }
            if (hasText(teamId)) {
                props.setHeader("teamId", teamId);
            }
            if (hasText(apiKeyId)) {
                props.setHeader("apiKeyId", apiKeyId);
            }
            Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);
            rabbitTemplate.send(out.getExchange(), out.getRoutingKey(), message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("budget threshold serialization failed", e);
        }
    }

    private static boolean isCrossed(
            BigDecimal beforeTotalUsd,
            BigDecimal afterTotalUsd,
            BigDecimal budgetUsd,
            BigDecimal thresholdPct
    ) {
        BigDecimal beforePct = safeRatio(beforeTotalUsd, budgetUsd);
        BigDecimal afterPct = safeRatio(afterTotalUsd, budgetUsd);
        return beforePct.compareTo(thresholdPct) < 0 && afterPct.compareTo(thresholdPct) >= 0;
    }

    private static BigDecimal safeRatio(BigDecimal total, BigDecimal budget) {
        if (total == null || budget == null || budget.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(budget, 8, RoundingMode.HALF_UP);
    }

    private static String resolveSubjectType(String userId, String teamId, String apiKeyId) {
        if (hasText(apiKeyId)) {
            return "API_KEY";
        }
        if (hasText(teamId)) {
            return "TEAM";
        }
        return "USER";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

