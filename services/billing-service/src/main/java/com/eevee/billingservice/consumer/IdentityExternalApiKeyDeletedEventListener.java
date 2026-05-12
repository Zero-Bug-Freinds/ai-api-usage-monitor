package com.eevee.billingservice.consumer;

import com.eevee.billingservice.service.PersonalExternalApiKeyExpenditurePurgeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.IdentityExternalApiKeyEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code identity.events} / {@code identity.external-api-key.status-changed} and purges billing
 * aggregates only for {@link ExternalApiKeyDeletedEvent}; other payloads are ignored.
 */
@Component
@ConditionalOnProperty(
        prefix = "billing.rabbit.identity-external-api-key-in",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class IdentityExternalApiKeyDeletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(IdentityExternalApiKeyDeletedEventListener.class);

    private final ObjectMapper objectMapper;
    private final PersonalExternalApiKeyExpenditurePurgeService purgeService;

    public IdentityExternalApiKeyDeletedEventListener(
            ObjectMapper objectMapper,
            PersonalExternalApiKeyExpenditurePurgeService purgeService
    ) {
        this.objectMapper = objectMapper;
        this.purgeService = purgeService;
    }

    @RabbitListener(queues = "${billing.rabbit.identity-external-api-key-in.queue}")
    public void onMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.hasNonNull("eventType")
                    || !IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED.equals(root.get("eventType").asText())) {
                return;
            }
            ExternalApiKeyDeletedEvent deleted = objectMapper.treeToValue(root, ExternalApiKeyDeletedEvent.class);
            if (deleted.userId() == null || deleted.userId().isBlank() || deleted.apiKeyId() == null) {
                log.warn("Skip EXTERNAL_API_KEY_DELETED payload: missing userId or apiKeyId");
                return;
            }
            purgeService.purgeForDeletedExternalApiKey(deleted.userId(), deleted.apiKeyId());
        } catch (Exception e) {
            log.error("Failed to handle identity external API key message", e);
            throw new IllegalStateException("identity external api key message handling failed", e);
        }
    }
}
