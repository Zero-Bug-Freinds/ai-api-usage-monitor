package com.eevee.usageservice.consumer;

import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.mq.TeamApiKeyChangedEvent;
import com.eevee.usageservice.mq.TeamApiKeyEventTypes;
import com.eevee.usageservice.service.ApiKeyMetadataSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "usage.rabbit.team-api-key", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TeamApiKeyChangedEventListener {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyChangedEventListener.class);

    private final ObjectMapper objectMapper;
    private final ApiKeyMetadataSyncService apiKeyMetadataSyncService;

    public TeamApiKeyChangedEventListener(
            ObjectMapper objectMapper,
            ApiKeyMetadataSyncService apiKeyMetadataSyncService
    ) {
        this.objectMapper = objectMapper;
        this.apiKeyMetadataSyncService = apiKeyMetadataSyncService;
    }

    @RabbitListener(queues = "${usage.rabbit.team-api-key.queue}")
    public void onMessage(String json) {
        try {
            TeamApiKeyChangedEvent event = objectMapper.readValue(json, TeamApiKeyChangedEvent.class);
            if (event.eventType() == null || event.eventType().isBlank()) {
                log.warn("Skipping team api key event without eventType");
                return;
            }
            switch (event.eventType()) {
                case TeamApiKeyEventTypes.TEAM_API_KEY_REGISTERED,
                     TeamApiKeyEventTypes.TEAM_API_KEY_UPDATED,
                     TeamApiKeyEventTypes.TEAM_API_KEY_DELETION_CANCELLED ->
                        apiKeyMetadataSyncService.upsertFromTeam(event, ApiKeyStatus.ACTIVE);
                case TeamApiKeyEventTypes.TEAM_API_KEY_DELETION_SCHEDULED ->
                        apiKeyMetadataSyncService.upsertFromTeam(event, ApiKeyStatus.DELETION_REQUESTED);
                case TeamApiKeyEventTypes.TEAM_API_KEY_DELETED ->
                        apiKeyMetadataSyncService.upsertFromTeam(event, ApiKeyStatus.DELETED);
                default -> log.debug("Ignoring non team-api-key eventType={}", event.eventType());
            }
        } catch (Exception e) {
            log.error("Failed to handle team API key event", e);
            throw new IllegalStateException("team api key event handling failed", e);
        }
    }
}
