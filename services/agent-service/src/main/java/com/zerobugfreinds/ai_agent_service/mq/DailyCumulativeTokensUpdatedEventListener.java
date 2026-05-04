package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.DailyCumulativeTokensUpdatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.DailyCumulativeTokenSnapshotService;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
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
		prefix = "ai-agent.rabbit.daily-cumulative-tokens",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class DailyCumulativeTokensUpdatedEventListener {

	private static final Logger log = LoggerFactory.getLogger(DailyCumulativeTokensUpdatedEventListener.class);

	private final ObjectMapper objectMapper;
	private final DailyCumulativeTokenSnapshotService dailyCumulativeTokenSnapshotService;
	private final EventDebugService eventDebugService;

	public DailyCumulativeTokensUpdatedEventListener(
			ObjectMapper objectMapper,
			DailyCumulativeTokenSnapshotService dailyCumulativeTokenSnapshotService,
			EventDebugService eventDebugService
	) {
		this.objectMapper = objectMapper;
		this.dailyCumulativeTokenSnapshotService = dailyCumulativeTokenSnapshotService;
		this.eventDebugService = eventDebugService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.daily-cumulative-tokens.queue}")
	public void onDailyCumulativeTokensUpdated(Message message) {
		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			DailyCumulativeTokensUpdatedEvent event = objectMapper.readValue(body, DailyCumulativeTokensUpdatedEvent.class);
			Map<String, String> headers = toStringHeaders(message);
			eventDebugService.record("DailyCumulativeTokensUpdatedEvent", headers, body);
			dailyCumulativeTokenSnapshotService.upsert(event);
		} catch (Exception ex) {
			log.error("Failed to handle DailyCumulativeTokensUpdatedEvent", ex);
			throw new IllegalStateException("daily cumulative tokens handling failed", ex);
		}
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
