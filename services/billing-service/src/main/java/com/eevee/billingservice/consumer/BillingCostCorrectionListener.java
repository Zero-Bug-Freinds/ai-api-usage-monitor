package com.eevee.billingservice.consumer;

import com.eevee.billingservice.events.BillingCostCorrectionAmqp;
import com.eevee.billingservice.service.BillingCostCorrectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BillingCostCorrectionListener {

    private static final Logger log = LoggerFactory.getLogger(BillingCostCorrectionListener.class);

    private final ObjectMapper objectMapper;
    private final BillingCostCorrectionService billingCostCorrectionService;

    public BillingCostCorrectionListener(ObjectMapper objectMapper, BillingCostCorrectionService billingCostCorrectionService) {
        this.objectMapper = objectMapper;
        this.billingCostCorrectionService = billingCostCorrectionService;
    }

    @RabbitListener(
            queues = "${billing.rabbit.correction-in.queue}",
            autoStartup = "${billing.rabbit.correction-in.enabled:true}"
    )
    public void onMessage(String json) {
        try {
            BillingCostCorrectionAmqp cmd = objectMapper.readValue(json, BillingCostCorrectionAmqp.class);
            billingCostCorrectionService.process(cmd);
        } catch (Exception e) {
            log.error("Failed to deserialize or process BillingCostCorrectionAmqp", e);
            throw new IllegalStateException("billing cost correction handling failed", e);
        }
    }
}
