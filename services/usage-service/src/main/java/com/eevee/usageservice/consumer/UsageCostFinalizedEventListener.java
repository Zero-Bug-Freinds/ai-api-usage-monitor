package com.eevee.usageservice.consumer;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usageservice.service.UsageCostFinalizedService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Applies billing-published USD estimates to {@code usage_recorded_log.estimated_cost}.
 */
@Component
@ConditionalOnProperty(prefix = "usage.rabbit.cost-finalized", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsageCostFinalizedEventListener {

    private static final Logger log = LoggerFactory.getLogger(UsageCostFinalizedEventListener.class);

    private final ObjectMapper objectMapper;
    private final UsageCostFinalizedService usageCostFinalizedService;

    public UsageCostFinalizedEventListener(ObjectMapper objectMapper, UsageCostFinalizedService usageCostFinalizedService) {
        this.objectMapper = objectMapper;
        this.usageCostFinalizedService = usageCostFinalizedService;
    }

    @RabbitListener(queues = "${usage.rabbit.cost-finalized.queue}")
    public void onMessage(String json) {
        try {
            UsageCostFinalizedEvent event = objectMapper.readValue(json, UsageCostFinalizedEvent.class);
            usageCostFinalizedService.applyCost(event);
        } catch (Exception e) {
            log.error("Failed to deserialize or apply UsageCostFinalizedEvent", e);
            throw new IllegalStateException("usage cost finalized handling failed", e);
        }
    }
}
