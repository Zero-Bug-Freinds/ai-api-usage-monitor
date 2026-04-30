package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import com.zerobugfreinds.ai_agent_service.service.TeamSnapshotService;
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
		prefix = "ai-agent.rabbit.team-domain",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class TeamDomainEventListener {

	private static final Logger log = LoggerFactory.getLogger(TeamDomainEventListener.class);
	private static final String TEAM_DELETED = "TEAM_DELETED";

	private final ObjectMapper objectMapper;
	private final TeamSnapshotService snapshotService;
	private final EventDebugService eventDebugService;

	public TeamDomainEventListener(
			ObjectMapper objectMapper,
			TeamSnapshotService snapshotService,
			EventDebugService eventDebugService
	) {
		this.objectMapper = objectMapper;
		this.snapshotService = snapshotService;
		this.eventDebugService = eventDebugService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.team-domain.queue}")
	public void onMessage(Message message) {
		try {
			String json = new String(message.getBody(), StandardCharsets.UTF_8);
			JsonNode root = objectMapper.readTree(json);
			Map<String, String> headers = toStringHeaders(message);
			String eventType = root.path("eventType").asText("");
			eventDebugService.record(eventType.isBlank() ? "UNKNOWN_TEAM_DOMAIN_EVENT" : eventType, headers, json);

			Long teamId = asLong(root.path("teamId"));
			if (teamId == null) {
				return;
			}

			if (TEAM_DELETED.equals(eventType)) {
				snapshotService.remove(teamId);
				return;
			}

			String teamName = asText(root.path("teamName"));
			if (teamName == null || teamName.isBlank()) {
				teamName = "Team " + teamId;
			}
			Instant occurredAt = asInstant(root.path("occurredAt"));
			snapshotService.upsert(new TeamSnapshotService.TeamSnapshot(teamId, teamName, occurredAt));
		} catch (Exception ex) {
			log.error("Failed to handle team domain event", ex);
			throw new IllegalStateException("team domain event handling failed", ex);
		}
	}

	private static Long asLong(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isNumber()) {
			return node.longValue();
		}
		if (node.isTextual()) {
			try {
				return Long.parseLong(node.asText());
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private static String asText(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		String text = node.asText(null);
		return text == null ? null : text.trim();
	}

	private static Instant asInstant(JsonNode node) {
		String raw = asText(node);
		if (raw == null || raw.isBlank()) {
			return Instant.now();
		}
		try {
			return Instant.parse(raw);
		} catch (Exception ignored) {
			return Instant.now();
		}
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
