package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import com.zerobugfreinds.ai_agent_service.service.IdentityApiKeySnapshotService;
import com.zerobugfreinds.identity.events.ExternalApiKeyBudgetChangedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import com.zerobugfreinds.identity.events.IdentityExternalApiKeyEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

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
	private final EventDebugService eventDebugService;

	public IdentityExternalApiKeyEventListener(
			ObjectMapper objectMapper,
			IdentityApiKeySnapshotService snapshotService,
			EventDebugService eventDebugService
	) {
		this.objectMapper = objectMapper;
		this.snapshotService = snapshotService;
		this.eventDebugService = eventDebugService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.identity-api-key.queue}")
	public void onMessage(Message message) {
		try {
			String json = new String(message.getBody(), StandardCharsets.UTF_8);
			JsonNode root = objectMapper.readTree(json);
			Map<String, String> headers = toStringHeaders(message);
			// 리스너에서 한 줄 추가 예시: eventDebugService.record(eventType, headers, json);
			if (root.has("eventType")) {
				String eventType = root.get("eventType").asText("");
				eventDebugService.record(eventType, headers, json);
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
				eventDebugService.record("ExternalApiKeyStatusChangedEvent", headers, json);
				ExternalApiKeyStatusChangedEvent changed =
						objectMapper.treeToValue(root, ExternalApiKeyStatusChangedEvent.class);
				snapshotService.upsertStatus(changed);
				return;
			}
			eventDebugService.record("UNKNOWN_IDENTITY_EVENT", headers, json);
			log.warn("Unrecognized identity external API key event payload");
		} catch (Exception ex) {
			log.error("Failed to handle identity external API key event", ex);
			throw new IllegalStateException("identity external api key event handling failed", ex);
		}
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
