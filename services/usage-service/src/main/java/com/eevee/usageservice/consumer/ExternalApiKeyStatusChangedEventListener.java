package com.eevee.usageservice.consumer;

import com.eevee.usageservice.service.ApiKeyMetadataSyncService;
import com.eevee.usageservice.mq.ExternalApiKeyDeletedEvent;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.IdentityExternalApiKeyEventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * identity.events 의 외부 API Key 메시지를 소비한다.
 * 동일 큐·라우팅 키로 {@link ExternalApiKeyStatusChangedEvent}(등록·별칭·상태)와
 * {@link ExternalApiKeyDeletedEvent}(물리 삭제) JSON 이 모두 올 수 있다.
 */
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
            JsonNode root = objectMapper.readTree(json);
            if (root.has("eventType")
                    && IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED.equals(root.get("eventType").asText())) {
                ExternalApiKeyDeletedEvent deleted = objectMapper.treeToValue(root, ExternalApiKeyDeletedEvent.class);
                apiKeyMetadataSyncService.handleExternalApiKeyDeleted(deleted);
                return;
            }
            if (root.has("schemaVersion")) {
                ExternalApiKeyStatusChangedEvent changed = objectMapper.readValue(json, ExternalApiKeyStatusChangedEvent.class);
                apiKeyMetadataSyncService.upsertFromIdentity(changed);
                return;
            }
            log.warn("Unrecognized identity external API key JSON (expected eventType={} or schemaVersion)", IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED);
        } catch (Exception e) {
            log.error("Failed to handle identity external API key event", e);
            throw new IllegalStateException("identity external api key event handling failed", e);
        }
    }
}
