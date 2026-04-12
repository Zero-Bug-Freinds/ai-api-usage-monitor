package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.pricing.OfficialProviderModelPriceCatalog;
import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Publishes {@link UsageCostFinalizedEvent} as JSON (UTF-8 string body) to the billing cost exchange.
 */
@Component
public class UsageCostFinalizedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UsageCostFinalizedEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final BillingRabbitProperties rabbitProperties;
    private final ObjectMapper objectMapper;

    public UsageCostFinalizedEventPublisher(
            RabbitTemplate rabbitTemplate,
            BillingRabbitProperties rabbitProperties,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitProperties = rabbitProperties;
        this.objectMapper = objectMapper;
    }

    public void publish(UsageRecordedEvent source, String model, BigDecimal estimatedCostUsd) {
        BillingRabbitProperties.CostOut out = rabbitProperties.getCostOut();
        if (!out.isEnabled()) {
            return;
        }
        UsageCostFinalizedEvent payload = new UsageCostFinalizedEvent(
                UsageCostFinalizedEvent.CURRENT_SCHEMA_VERSION,
                source.eventId(),
                estimatedCostUsd,
                Instant.now(),
                OfficialProviderModelPriceCatalog.DOCUMENTED_AS_OF,
                source.provider(),
                model
        );
        log.debug(
                "Publishing UsageCostFinalizedEvent eventId={} exchange={} routingKey={}",
                source.eventId(),
                out.getExchange(),
                out.getRoutingKey());
        try {
            String json = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend(out.getExchange(), out.getRoutingKey(), json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("usage cost finalized serialization failed", e);
        }
    }
}
