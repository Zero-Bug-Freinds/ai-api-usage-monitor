package com.eevee.usageservice.consumer;

import com.eevee.usageservice.mq.TeamApiKeyDeletedEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionCancelledEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionScheduledEvent;
import com.eevee.usageservice.mq.TeamApiKeyEventTypes;
import com.eevee.usageservice.mq.TeamApiKeyRegisteredEvent;
import com.eevee.usageservice.mq.TeamApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.TeamApiKeyUpdatedEvent;
import com.eevee.usageservice.service.ApiKeyMetadataSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "usage.rabbit.team-api-key", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TeamApiKeyEventListener {
    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyEventListener.class);

    private final ObjectMapper objectMapper;
    private final ApiKeyMetadataSyncService apiKeyMetadataSyncService;

    public TeamApiKeyEventListener(ObjectMapper objectMapper, ApiKeyMetadataSyncService apiKeyMetadataSyncService) {
        this.objectMapper = objectMapper;
        this.apiKeyMetadataSyncService = apiKeyMetadataSyncService;
    }

    @RabbitListener(queues = "${usage.rabbit.team-api-key.queue}")
    public void onMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "";
            switch (eventType) {
                case TeamApiKeyEventTypes.TEAM_API_KEY_REGISTERED -> {
                    TeamApiKeyRegisteredEvent event = objectMapper.treeToValue(root, TeamApiKeyRegisteredEvent.class);
                    apiKeyMetadataSyncService.upsertFromTeamRegistered(event);
                }
                case TeamApiKeyEventTypes.TEAM_API_KEY_UPDATED -> {
                    TeamApiKeyUpdatedEvent event = objectMapper.treeToValue(root, TeamApiKeyUpdatedEvent.class);
                    apiKeyMetadataSyncService.upsertFromTeamUpdated(event);
                }
                case TeamApiKeyEventTypes.TEAM_API_KEY_DELETED -> {
                    TeamApiKeyDeletedEvent event = objectMapper.treeToValue(root, TeamApiKeyDeletedEvent.class);
                    apiKeyMetadataSyncService.handleTeamDeleted(event);
                }
                case TeamApiKeyEventTypes.TEAM_API_KEY_DELETION_SCHEDULED -> {
                    TeamApiKeyDeletionScheduledEvent event = objectMapper.treeToValue(root, TeamApiKeyDeletionScheduledEvent.class);
                    apiKeyMetadataSyncService.handleTeamDeletionScheduled(event);
                }
                case TeamApiKeyEventTypes.TEAM_API_KEY_DELETION_CANCELLED -> {
                    TeamApiKeyDeletionCancelledEvent event = objectMapper.treeToValue(root, TeamApiKeyDeletionCancelledEvent.class);
                    apiKeyMetadataSyncService.handleTeamDeletionCancelled(event);
                }
                case TeamApiKeyEventTypes.TEAM_API_KEY_STATUS_CHANGED -> {
                    TeamApiKeyStatusChangedEvent event = objectMapper.treeToValue(root, TeamApiKeyStatusChangedEvent.class);
                    apiKeyMetadataSyncService.upsertFromTeamStatusChanged(event);
                }
                default -> log.debug("Ignoring non-team-api-key eventType={} in team queue", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to handle team api key event", e);
            throw new IllegalStateException("team api key event handling failed", e);
        }
    }
}
