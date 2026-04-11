package com.eevee.usageservice.consumer;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usageservice.service.UsageCostApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes JSON payloads published by billing-service (same format as {@link UsageCostFinalizedEvent}).
 */
@Component
public class UsageCostFinalizedEventListener {

    private static final Logger log = LoggerFactory.getLogger(UsageCostFinalizedEventListener.class);

    private final ObjectMapper objectMapper;
    private final UsageCostApplicationService usageCostApplicationService;

    public UsageCostFinalizedEventListener(
            ObjectMapper objectMapper,
            UsageCostApplicationService usageCostApplicationService
    ) {
        this.objectMapper = objectMapper;
        this.usageCostApplicationService = usageCostApplicationService;
    }

    @RabbitListener(queues = "${usage.rabbit.cost-queue}")
    public void onMessage(String json) {
        try {
            UsageCostFinalizedEvent event = objectMapper.readValue(json, UsageCostFinalizedEvent.class);
            usageCostApplicationService.applyCost(event);
        } catch (Exception e) {
            log.error("Failed to deserialize or apply UsageCostFinalizedEvent", e);
            throw new IllegalStateException("usage cost event handling failed", e);
        }
    }
}
