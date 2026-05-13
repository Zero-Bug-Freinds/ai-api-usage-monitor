package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zerobugfreinds.ai_agent_service.service.ApiKeyUsageDataCleanupService;
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
	private static final String EXTERNAL_API_KEY_BUDGET_CHANGED = "EXTERNAL_API_KEY_BUDGET_CHANGED";
	private static final String EVENT_TYPE_FIELD = "eventType";

	private final ObjectMapper objectMapper;
	private final IdentityApiKeySnapshotService snapshotService;
	private final ApiKeyUsageDataCleanupService apiKeyUsageDataCleanupService;
	private final EventDebugService eventDebugService;

	public IdentityExternalApiKeyEventListener(
			ObjectMapper objectMapper,
			IdentityApiKeySnapshotService snapshotService,
			ApiKeyUsageDataCleanupService apiKeyUsageDataCleanupService,
			EventDebugService eventDebugService
	) {
		this.objectMapper = objectMapper;
		this.snapshotService = snapshotService;
		this.apiKeyUsageDataCleanupService = apiKeyUsageDataCleanupService;
		this.eventDebugService = eventDebugService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.identity-api-key.queue}")
	public void onMessage(Message message) {
		try {
			String json = new String(message.getBody(), StandardCharsets.UTF_8);
			JsonNode root = objectMapper.readTree(json);
			Map<String, String> headers = toStringHeaders(message);

			// 1) Physical delete — mirror usage-service ExternalApiKeyStatusChangedEventListener
			if (root.has(EVENT_TYPE_FIELD)
					&& IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED.equals(root.get(EVENT_TYPE_FIELD).asText())) {
				eventDebugService.record(IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED, headers, json);
				ExternalApiKeyDeletedEvent deleted = parseDeletedEvent(root);
				snapshotService.applyDeleted(deleted);
				if (!deleted.retainLogs() && deleted.apiKeyId() != null) {
					apiKeyUsageDataCleanupService.purgeByApiKeyId(String.valueOf(deleted.apiKeyId()));
				}
				return;
			}

			// 2) Budget — agent snapshots persist monthlyBudgetUsd (usage metadata does not). Must precede schemaVersion
			// because budget payloads also include schemaVersion.
			if (root.has(EVENT_TYPE_FIELD) && isBudgetChangedEventType(root.get(EVENT_TYPE_FIELD).asText())) {
				eventDebugService.record(root.get(EVENT_TYPE_FIELD).asText(), headers, json);
				ExternalApiKeyBudgetChangedEvent budgetChanged =
						objectMapper.readValue(json, ExternalApiKeyBudgetChangedEvent.class);
				snapshotService.upsertBudget(budgetChanged);
				return;
			}

			// 3) Status / alias — same branch as usage-service (flat ExternalApiKeyStatusChangedEvent from Identity)
			if (root.has("schemaVersion")) {
				eventDebugService.record("ExternalApiKeyStatusChangedEvent", headers, json);
				ExternalApiKeyStatusChangedEvent changed = parseStatusChangedEvent(root);
				snapshotService.upsertStatus(changed);
				return;
			}

			eventDebugService.record("UNKNOWN_IDENTITY_EVENT", headers, json);
			log.warn(
					"Unrecognized identity external API key event payload (expected eventType={} or schemaVersion)",
					IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED);
		} catch (Exception ex) {
			String messageId = message.getMessageProperties().getMessageId();
			log.error("Failed to handle identity external API key event. messageId={}", messageId, ex);
			throw new IllegalStateException("Failed to process identity external API key event", ex);
		}
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}

	private static boolean isBudgetChangedEventType(String eventType) {
		return IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_BUDGET_CHANGED.equals(eventType)
				|| EXTERNAL_API_KEY_BUDGET_CHANGED.equals(eventType)
				|| "ExternalApiKeyBudgetChangedEvent".equals(eventType);
	}

	private ExternalApiKeyDeletedEvent parseDeletedEvent(JsonNode payloadNode) throws Exception {
		if (payloadNode instanceof ObjectNode objectNode) {
			if (!objectNode.has("apiKeyId") && objectNode.has("keyId")) {
				objectNode.set("apiKeyId", objectNode.get("keyId"));
			}
			if (!objectNode.has("retainLogs") || objectNode.get("retainLogs").isNull()) {
				objectNode.set("retainLogs", BooleanNode.TRUE);
			}
		}
		return objectMapper.treeToValue(payloadNode, ExternalApiKeyDeletedEvent.class);
	}

	private ExternalApiKeyStatusChangedEvent parseStatusChangedEvent(JsonNode payloadNode) throws Exception {
		if (payloadNode instanceof ObjectNode objectNode) {
			ObjectNode mutable = objectNode.deepCopy();
			mutable.remove(EVENT_TYPE_FIELD);
			return objectMapper.treeToValue(mutable, ExternalApiKeyStatusChangedEvent.class);
		}
		return objectMapper.treeToValue(payloadNode, ExternalApiKeyStatusChangedEvent.class);
	}
}
