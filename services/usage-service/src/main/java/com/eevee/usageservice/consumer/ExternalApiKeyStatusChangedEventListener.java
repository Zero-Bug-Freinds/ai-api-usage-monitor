package com.eevee.usageservice.consumer;

import com.eevee.usageservice.service.ApiKeyMetadataSyncService;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "usage.rabbit.identity-api-key", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExternalApiKeyStatusChangedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyStatusChangedEventListener.class);

    private final ObjectMapper objectMapper;
    private final ApiKeyMetadataSyncService apiKeyMetadataSyncService;

    public ExternalApiKeyStatusChangedEventListener(
            ObjectMapper objectMapper,
            ApiKeyMetadataSyncService apiKeyMetadataSyncService
    ) {
        this.objectMapper = objectMapper;
        this.apiKeyMetadataSyncService = apiKeyMetadataSyncService;
    }

    @RabbitListener(queues = "${usage.rabbit.identity-api-key.queue}")
    public void onMessage(String json) {
        try {
            ExternalApiKeyStatusChangedEvent event = objectMapper.readValue(json, ExternalApiKeyStatusChangedEvent.class);
            apiKeyMetadataSyncService.upsertFromIdentity(event);
        } catch (Exception e) {
            log.error("Failed to deserialize or upsert ExternalApiKeyStatusChangedEvent", e);
            throw new IllegalStateException("identity external api key event handling failed", e);
        }
    }
}
