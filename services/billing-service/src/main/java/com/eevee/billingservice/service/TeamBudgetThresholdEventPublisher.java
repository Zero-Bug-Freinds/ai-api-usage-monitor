package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.events.BillingTeamBudgetThresholdReachedEvent;
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
import java.util.ArrayList;
import java.util.List;

@Component
public class TeamBudgetThresholdEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TeamBudgetThresholdEventPublisher.class);

    private static final int PERCENT_STEP = 1;
    private static final List<BigDecimal> DEFAULT_THRESHOLDS = buildPercentThresholdsInclusive(PERCENT_STEP, 100, PERCENT_STEP);

    private static List<BigDecimal> buildPercentThresholdsInclusive(int fromPercent, int toPercent, int stepPercent) {
        if (fromPercent <= 0 || toPercent <= 0 || toPercent < fromPercent || stepPercent <= 0) {
            throw new IllegalArgumentException("Invalid percent threshold range");
        }
        List<BigDecimal> thresholds = new ArrayList<>((toPercent - fromPercent) / stepPercent + 1);
        for (int pct = fromPercent; pct <= toPercent; pct += stepPercent) {
            thresholds.add(BigDecimal.valueOf(pct).movePointLeft(2));
        }
        return List.copyOf(thresholds);
    }

    private final RabbitTemplate rabbitTemplate;
    private final BillingRabbitProperties rabbitProperties;
    private final ObjectMapper objectMapper;

    public TeamBudgetThresholdEventPublisher(
            RabbitTemplate rabbitTemplate,
            BillingRabbitProperties rabbitProperties,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitProperties = rabbitProperties;
        this.objectMapper = objectMapper;
    }

    public void publishIfCrossed(
            String triggerUserId,
            long teamId,
            LocalDate monthStart,
            BigDecimal monthlyTotalUsdBefore,
            BigDecimal monthlyTotalUsdAfter,
            BigDecimal monthlyBudgetUsd
    ) {
        BillingRabbitProperties.TeamBudgetOut out = rabbitProperties.getTeamBudgetOut();
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
            publishOne(out, triggerUserId, teamId, monthStart, threshold, monthlyTotalUsdAfter, monthlyBudgetUsd);
        }
    }

    private void publishOne(
            BillingRabbitProperties.TeamBudgetOut out,
            String triggerUserId,
            long teamId,
            LocalDate monthStart,
            BigDecimal thresholdPct,
            BigDecimal monthlyTotalUsd,
            BigDecimal monthlyBudgetUsd
    ) {
        BillingTeamBudgetThresholdReachedEvent payload = new BillingTeamBudgetThresholdReachedEvent(
                BillingTeamBudgetThresholdReachedEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                teamId,
                triggerUserId,
                monthStart,
                thresholdPct,
                monthlyTotalUsd,
                monthlyBudgetUsd
        );

        log.debug(
                "Publishing BillingTeamBudgetThresholdReachedEvent teamId={} monthStart={} thresholdPct={} exchange={} routingKey={}",
                teamId,
                monthStart,
                thresholdPct,
                out.getExchange(),
                out.getRoutingKey()
        );

        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding(StandardCharsets.UTF_8.name());
            props.setHeader("subjectType", "TEAM");
            props.setHeader("teamId", String.valueOf(teamId));
            if (triggerUserId != null && !triggerUserId.isBlank()) {
                props.setHeader("triggerUserId", triggerUserId);
            }
            Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);
            rabbitTemplate.send(out.getExchange(), out.getRoutingKey(), message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("team budget threshold serialization failed", e);
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
}

