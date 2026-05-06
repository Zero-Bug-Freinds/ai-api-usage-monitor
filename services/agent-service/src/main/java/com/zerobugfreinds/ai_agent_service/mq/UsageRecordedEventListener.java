package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import com.zerobugfreinds.ai_agent_service.service.UsageRecordedTokenRollupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(
		prefix = "ai-agent.rabbit.usage-recorded",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class UsageRecordedEventListener {

	private static final Logger log = LoggerFactory.getLogger(UsageRecordedEventListener.class);

	private final ObjectMapper objectMapper;
	private final UsageRecordedTokenRollupService rollupService;
	private final EventDebugService eventDebugService;

	public UsageRecordedEventListener(
			ObjectMapper objectMapper,
			UsageRecordedTokenRollupService rollupService,
			EventDebugService eventDebugService
	) {
		this.objectMapper = objectMapper;
		this.rollupService = rollupService;
		this.eventDebugService = eventDebugService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.usage-recorded.queue}")
	public void onUsageRecorded(Message message) {
		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			UsageRecordedEvent event = objectMapper.readValue(body, UsageRecordedEvent.class);
			Map<String, String> headers = toStringHeaders(message);
			eventDebugService.record("UsageRecordedEvent", headers, body);

			TokenUsage tokenUsage = event.tokenUsage();
			if (tokenUsage == null) {
				log.debug("Skip usage.recorded rollup because tokenUsage is null. keyId={}", event.apiKeyId());
				return;
			}

			String scopeType = resolveScopeType(event.apiKeySource());
			String scopeId = scopeType.equals("TEAM") ? event.teamId() : event.userId();
			if (scopeId == null || scopeId.isBlank()) {
				log.debug("Skip usage.recorded rollup because scopeId is missing. keyId={}, apiKeySource={}",
						event.apiKeyId(), event.apiKeySource());
				return;
			}

			rollupService.add(
					event.apiKeyId(),
					scopeType,
					scopeId,
					event.occurredAt(),
					tokenUsage.promptTokens(),
					tokenUsage.completionTokens()
			);
		} catch (Exception ex) {
			log.error("Failed to handle UsageRecordedEvent", ex);
			throw new IllegalStateException("usage recorded event handling failed", ex);
		}
	}

	private static String resolveScopeType(String apiKeySource) {
		String source = apiKeySource == null ? "" : apiKeySource.trim().toLowerCase(Locale.ROOT);
		return switch (source) {
			case "team" -> "TEAM";
			case "managed", "mock" -> "PERSONAL";
			default -> "PERSONAL";
		};
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
