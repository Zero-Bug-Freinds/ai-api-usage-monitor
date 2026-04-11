package com.eevee.billingservice.consumer;

import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.billingservice.service.BillingRecordedService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BillingUsageRecordedEventListener {

    private static final Logger log = LoggerFactory.getLogger(BillingUsageRecordedEventListener.class);

    private final ObjectMapper objectMapper;
    private final BillingRecordedService billingRecordedService;

    public BillingUsageRecordedEventListener(ObjectMapper objectMapper, BillingRecordedService billingRecordedService) {
        this.objectMapper = objectMapper;
        this.billingRecordedService = billingRecordedService;
    }

    @RabbitListener(queues = "${billing.rabbit.queue}")
    public void onMessage(String json) {
        try {
            UsageRecordedEvent event = objectMapper.readValue(json, UsageRecordedEvent.class);
            billingRecordedService.process(event);
        } catch (Exception e) {
            log.error("Failed to deserialize or process UsageRecordedEvent for billing", e);
            throw new IllegalStateException("billing event handling failed", e);
        }
    }
}
