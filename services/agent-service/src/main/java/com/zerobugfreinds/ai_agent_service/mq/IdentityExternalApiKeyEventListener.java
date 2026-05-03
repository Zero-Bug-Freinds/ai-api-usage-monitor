package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
	private static final String EXTERNAL_API_KEY_STATUS_CHANGED = "EXTERNAL_API_KEY_STATUS_CHANGED";
	private static final String EXTERNAL_API_KEY_BUDGET_CHANGED = "EXTERNAL_API_KEY_BUDGET_CHANGED";
	private static final String EXTERNAL_API_KEY_DELETED = "EXTERNAL_API_KEY_DELETED";

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
			JsonNode payloadNode = extractPayloadNode(root);
			// 리스너에서 한 줄 추가 예시: eventDebugService.record(eventType, headers, json);
			if (root.has("eventType")) {
				String eventType = root.get("eventType").asText("");
				eventDebugService.record(eventType, headers, json);
				if (EXTERNAL_API_KEY_STATUS_CHANGED.equals(eventType)) {
					ExternalApiKeyStatusChangedEvent changed =
							objectMapper.treeToValue(payloadNode, ExternalApiKeyStatusChangedEvent.class);
					snapshotService.upsertStatus(changed);
					return;
				}
				if (isDeletedEventType(eventType)) {
					ExternalApiKeyDeletedEvent deleted = parseDeletedEvent(payloadNode);
					snapshotService.delete(deleted);
					return;
				}
				if (isBudgetChangedEventType(eventType)) {
					ExternalApiKeyBudgetChangedEvent budgetChanged = parseBudgetChangedEvent(payloadNode);
					snapshotService.upsertBudget(budgetChanged);
					return;
				}
			}
			if (payloadNode.has("monthlyBudgetUsd") && payloadNode.has("keyId")) {
				eventDebugService.record("ExternalApiKeyBudgetChangedEvent", headers, json);
				ExternalApiKeyBudgetChangedEvent budgetChanged = parseBudgetChangedEvent(payloadNode);
				snapshotService.upsertBudget(budgetChanged);
				return;
			}
			if ((payloadNode.has("apiKeyId") || payloadNode.has("keyId")) && payloadNode.has("userId") && payloadNode.has("retainLogs")) {
				eventDebugService.record("ExternalApiKeyDeletedEvent", headers, json);
				ExternalApiKeyDeletedEvent deleted = parseDeletedEvent(payloadNode);
				snapshotService.delete(deleted);
				return;
			}
			if (payloadNode.has("schemaVersion") && payloadNode.has("keyId")) {
				eventDebugService.record("ExternalApiKeyStatusChangedEvent", headers, json);
				ExternalApiKeyStatusChangedEvent changed =
						objectMapper.treeToValue(payloadNode, ExternalApiKeyStatusChangedEvent.class);
				snapshotService.upsertStatus(changed);
				return;
			}
			eventDebugService.record("UNKNOWN_IDENTITY_EVENT", headers, json);
			log.warn("Unrecognized identity external API key event payload");
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

	private static JsonNode extractPayloadNode(JsonNode root) {
		JsonNode data = root.get("data");
		if (data != null && data.isObject()) {
			return data;
		}
		return root;
	}

	private static boolean isBudgetChangedEventType(String eventType) {
		return IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_BUDGET_CHANGED.equals(eventType)
				|| EXTERNAL_API_KEY_BUDGET_CHANGED.equals(eventType)
				|| "ExternalApiKeyBudgetChangedEvent".equals(eventType);
	}

	private static boolean isDeletedEventType(String eventType) {
		return IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED.equals(eventType)
				|| EXTERNAL_API_KEY_DELETED.equals(eventType)
				|| "ExternalApiKeyDeletedEvent".equals(eventType);
	}

	private ExternalApiKeyDeletedEvent parseDeletedEvent(JsonNode payloadNode) throws Exception {
		if (payloadNode instanceof ObjectNode objectNode) {
			if (!objectNode.has("apiKeyId") && objectNode.has("keyId")) {
				objectNode.set("apiKeyId", objectNode.get("keyId"));
			}
		}
		return objectMapper.treeToValue(payloadNode, ExternalApiKeyDeletedEvent.class);
	}

	private ExternalApiKeyBudgetChangedEvent parseBudgetChangedEvent(JsonNode payloadNode) throws Exception {
		return objectMapper.treeToValue(payloadNode, ExternalApiKeyBudgetChangedEvent.class);
	}
}
