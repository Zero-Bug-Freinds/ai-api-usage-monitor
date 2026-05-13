package com.eevee.billingservice.consumer;

import com.eevee.billingservice.event.TeamDomainAmqpEventTypes;
import com.eevee.billingservice.service.TeamApiKeyExpenditurePurgeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code team.events} / {@code team.api.key.#} and purges billing only for
 * {@link TeamDomainAmqpEventTypes#TEAM_API_KEY_DELETED}.
 */
@Component
@ConditionalOnProperty(
        prefix = "billing.rabbit.team-domain-in",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TeamApiKeyDeletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyDeletedEventListener.class);

    private final ObjectMapper objectMapper;
    private final TeamApiKeyExpenditurePurgeService purgeService;

    public TeamApiKeyDeletedEventListener(ObjectMapper objectMapper, TeamApiKeyExpenditurePurgeService purgeService) {
        this.objectMapper = objectMapper;
        this.purgeService = purgeService;
    }

    @RabbitListener(queues = "${billing.rabbit.team-domain-in.queue}")
    public void onMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.hasNonNull("eventType")
                    || !TeamDomainAmqpEventTypes.TEAM_API_KEY_DELETED.equals(root.get("eventType").asText())) {
                return;
            }
            if (!root.hasNonNull("apiKeyId")) {
                log.warn("Skip TEAM_API_KEY_DELETED payload: missing apiKeyId");
                return;
            }
            long apiKeyId = root.get("apiKeyId").asLong();
            purgeService.purgeForDeletedTeamApiKey(apiKeyId);
        } catch (Exception e) {
            log.error("Failed to handle team domain API key message", e);
            throw new IllegalStateException("team domain api key message handling failed", e);
        }
    }
}
