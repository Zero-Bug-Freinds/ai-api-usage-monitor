package com.eevee.usageservice.consumer;

import com.eevee.usageservice.mq.UsageSummaryAggregationMessage;
import com.eevee.usageservice.service.UsageAggregationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "usage.rabbit.summary-aggregation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsageSummaryAggregationEventListener {

    private static final Logger log = LoggerFactory.getLogger(UsageSummaryAggregationEventListener.class);

    private final ObjectMapper objectMapper;
    private final UsageAggregationService usageAggregationService;

    public UsageSummaryAggregationEventListener(ObjectMapper objectMapper, UsageAggregationService usageAggregationService) {
        this.objectMapper = objectMapper;
        this.usageAggregationService = usageAggregationService;
    }

    @RabbitListener(
            queues = "${usage.rabbit.summary-aggregation.queue}",
            containerFactory = "summaryAggregationRabbitListenerContainerFactory"
    )
    public void onMessage(String json) {
        try {
            UsageSummaryAggregationMessage message = objectMapper.readValue(json, UsageSummaryAggregationMessage.class);
            usageAggregationService.applyFromEvent(message);
        } catch (DataAccessException e) {
            log.error("Transient DB error during summary aggregation, will retry", e);
            throw new IllegalStateException("summary aggregation db error", e);
        } catch (Exception e) {
            log.error("Failed to process summary aggregation message", e);
            throw new IllegalStateException("summary aggregation handling failed", e);
        }
    }
}
