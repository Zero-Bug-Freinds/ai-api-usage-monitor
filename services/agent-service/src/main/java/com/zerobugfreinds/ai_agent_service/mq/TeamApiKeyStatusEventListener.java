package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import com.zerobugfreinds.ai_agent_service.service.TeamApiKeySnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(
		prefix = "ai-agent.rabbit.team-api-key",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class TeamApiKeyStatusEventListener {

	private static final Logger log = LoggerFactory.getLogger(TeamApiKeyStatusEventListener.class);
	private static final String TEAM_API_KEY_STATUS_CHANGED = "TEAM_API_KEY_STATUS_CHANGED";

	private final ObjectMapper objectMapper;
	private final TeamApiKeySnapshotService snapshotService;
	private final EventDebugService eventDebugService;

	public TeamApiKeyStatusEventListener(
			ObjectMapper objectMapper,
			TeamApiKeySnapshotService snapshotService,
			EventDebugService eventDebugService
	) {
		this.objectMapper = objectMapper;
		this.snapshotService = snapshotService;
		this.eventDebugService = eventDebugService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.team-api-key.queue}")
	public void onMessage(Message message) {
		try {
			String json = new String(message.getBody(), StandardCharsets.UTF_8);
			JsonNode root = objectMapper.readTree(json);
			Map<String, String> headers = toStringHeaders(message);
			String eventType = root.path("eventType").asText("");
			eventDebugService.record(eventType.isBlank() ? "UNKNOWN_TEAM_EVENT" : eventType, headers, json);

			if (!TEAM_API_KEY_STATUS_CHANGED.equals(eventType)) {
				return;
			}

			Long teamId = asLong(root, "teamId");
			Long teamApiKeyId = asLong(root, "teamApiKeyId");
			if (teamId == null || teamApiKeyId == null) {
				log.warn("TEAM_API_KEY_STATUS_CHANGED missing teamId/teamApiKeyId");
				return;
			}
			Instant occurredAt = root.hasNonNull("occurredAt")
					? Instant.parse(root.get("occurredAt").asText())
					: Instant.now();

			snapshotService.upsert(
					new TeamApiKeySnapshotService.TeamApiKeySnapshot(
							teamId,
							teamApiKeyId,
							asText(root, "ownerUserId"),
							asText(root, "visibility"),
							asText(root, "alias"),
							asText(root, "provider"),
							asText(root, "status"),
							asBoolean(root, "retainLogs"),
							occurredAt
					)
			);
		} catch (Exception ex) {
			log.error("Failed to handle team API key status event", ex);
			throw new IllegalStateException("team api key status event handling failed", ex);
		}
	}

	private static Long asLong(JsonNode root, String field) {
		return root.hasNonNull(field) ? root.get(field).asLong() : null;
	}

	private static String asText(JsonNode root, String field) {
		return root.hasNonNull(field) ? root.get(field).asText() : null;
	}

	private static Boolean asBoolean(JsonNode root, String field) {
		return root.hasNonNull(field) ? root.get(field).asBoolean() : null;
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
