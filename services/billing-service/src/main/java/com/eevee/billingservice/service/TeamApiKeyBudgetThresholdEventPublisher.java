package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.events.BillingTeamApiKeyBudgetThresholdReachedEvent;
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
public class TeamApiKeyBudgetThresholdEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyBudgetThresholdEventPublisher.class);

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

    public TeamApiKeyBudgetThresholdEventPublisher(
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
            long teamApiKeyId,
            String provider,
            String apiKeyAlias,
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
            publishOne(out, triggerUserId, teamId, teamApiKeyId, provider, apiKeyAlias, monthStart, threshold, monthlyTotalUsdAfter, monthlyBudgetUsd);
        }
    }

    private void publishOne(
            BillingRabbitProperties.TeamBudgetOut out,
            String triggerUserId,
            long teamId,
            long teamApiKeyId,
            String provider,
            String apiKeyAlias,
            LocalDate monthStart,
            BigDecimal thresholdPct,
            BigDecimal monthlyTotalUsd,
            BigDecimal monthlyBudgetUsd
    ) {
        BillingTeamApiKeyBudgetThresholdReachedEvent payload = new BillingTeamApiKeyBudgetThresholdReachedEvent(
                BillingTeamApiKeyBudgetThresholdReachedEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                teamId,
                triggerUserId,
                teamApiKeyId,
                provider,
                apiKeyAlias,
                monthStart,
                thresholdPct,
                monthlyTotalUsd,
                monthlyBudgetUsd
        );

        log.debug(
                "Publishing BillingTeamApiKeyBudgetThresholdReachedEvent teamId={} teamApiKeyId={} monthStart={} thresholdPct={} exchange={} routingKey={}",
                teamId,
                teamApiKeyId,
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
            props.setHeader("subjectType", "TEAM_API_KEY");
            props.setHeader("teamId", String.valueOf(teamId));
            props.setHeader("teamApiKeyId", String.valueOf(teamApiKeyId));
            if (triggerUserId != null && !triggerUserId.isBlank()) {
                props.setHeader("triggerUserId", triggerUserId);
            }
            if (provider != null && !provider.isBlank()) {
                props.setHeader("provider", provider);
            }
            if (apiKeyAlias != null && !apiKeyAlias.isBlank()) {
                props.setHeader("apiKeyAlias", apiKeyAlias);
            }
            Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);
            rabbitTemplate.send(out.getExchange(), out.getRoutingKey(), message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("team api key budget threshold serialization failed", e);
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

