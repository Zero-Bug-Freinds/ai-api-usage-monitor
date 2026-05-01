package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.events.BillingCostCorrectedEvent;
import com.eevee.billingservice.events.BillingCostCorrectionAmqp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class BillingCostCorrectedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BillingCostCorrectedEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final BillingRabbitProperties rabbitProperties;
    private final ObjectMapper objectMapper;

    public BillingCostCorrectedEventPublisher(
            RabbitTemplate rabbitTemplate,
            BillingRabbitProperties rabbitProperties,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitProperties = rabbitProperties;
        this.objectMapper = objectMapper;
    }

    public void publish(BillingCostCorrectionAmqp applied, Instant occurredAt) {
        BillingRabbitProperties.CorrectionOut out = rabbitProperties.getCorrectionOut();
        if (!out.isEnabled()) {
            return;
        }

        BillingCostCorrectedEvent payload = new BillingCostCorrectedEvent(
                BillingCostCorrectedEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                applied.correctionEventId(),
                applied.userId(),
                applied.apiKeyId(),
                applied.monthStartDate(),
                applied.deltaCostUsd(),
                applied.aggDate(),
                applied.provider(),
                applied.model(),
                applied.optionalCorrectedTotalUsdForScope(),
                applied.relatedUsageEventId()
        );

        log.debug(
                "Publishing BillingCostCorrectedEvent correctionEventId={} exchange={} routingKey={}",
                applied.correctionEventId(),
                out.getExchange(),
                out.getRoutingKey());

        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding(StandardCharsets.UTF_8.name());
            props.setHeader("userId", applied.userId());
            props.setHeader("apiKeyId", applied.apiKeyId());
            Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);
            rabbitTemplate.send(out.getExchange(), out.getRoutingKey(), message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("billing cost corrected serialization failed", e);
        }
    }
}
