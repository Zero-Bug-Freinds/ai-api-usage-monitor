package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.IdentityApiKeySnapshotService;
import com.zerobugfreinds.identity.events.ExternalApiKeyBudgetChangedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import com.zerobugfreinds.identity.events.IdentityExternalApiKeyEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		prefix = "ai-agent.rabbit.identity-api-key",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class IdentityExternalApiKeyEventListener {

	private static final Logger log = LoggerFactory.getLogger(IdentityExternalApiKeyEventListener.class);

	private final ObjectMapper objectMapper;
	private final IdentityApiKeySnapshotService snapshotService;

	public IdentityExternalApiKeyEventListener(
			ObjectMapper objectMapper,
			IdentityApiKeySnapshotService snapshotService
	) {
		this.objectMapper = objectMapper;
		this.snapshotService = snapshotService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.identity-api-key.queue}")
	public void onMessage(String json) {
		try {
			JsonNode root = objectMapper.readTree(json);
			if (root.has("eventType")) {
				String eventType = root.get("eventType").asText("");
				if (IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED.equals(eventType)) {
					ExternalApiKeyDeletedEvent deleted = objectMapper.treeToValue(root, ExternalApiKeyDeletedEvent.class);
					snapshotService.delete(deleted);
					return;
				}
				if (IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_BUDGET_CHANGED.equals(eventType)) {
					ExternalApiKeyBudgetChangedEvent budgetChanged =
							objectMapper.treeToValue(root, ExternalApiKeyBudgetChangedEvent.class);
					snapshotService.upsertBudget(budgetChanged);
					return;
				}
			}
			if (root.has("schemaVersion")) {
				ExternalApiKeyStatusChangedEvent changed =
						objectMapper.treeToValue(root, ExternalApiKeyStatusChangedEvent.class);
				snapshotService.upsertStatus(changed);
				return;
			}
			log.warn("Unrecognized identity external API key event payload");
		} catch (Exception ex) {
			log.error("Failed to handle identity external API key event", ex);
			throw new IllegalStateException("identity external api key event handling failed", ex);
		}
	}
}
