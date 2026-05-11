package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.AiProvider;
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

			ResolvedScope resolved = resolveScope(event);
			if (resolved == null) {
				log.debug("Skip usage.recorded rollup because scopeId is missing. keyId={}, apiKeySource={}, teamApiKeyId={}",
						event.apiKeyId(), event.apiKeySource(), event.teamApiKeyId());
				return;
			}

			rollupService.add(
					event.apiKeyId(),
					resolved.scopeType(),
					resolved.scopeId(),
					event.occurredAt(),
					tokenUsage.promptTokens(),
					resolveOutputTokens(event.provider(), tokenUsage),
					resolveReasoningTokens(tokenUsage),
					event.latencyMs()
			);
		} catch (Exception ex) {
			log.error("Failed to handle UsageRecordedEvent", ex);
			throw new IllegalStateException("usage recorded event handling failed", ex);
		}
	}

	/**
	 * 사용량 이벤트의 scope 결정.
	 *
	 * <p>proxy 가 reverse-lookup 경로로 키를 식별하면 {@code apiKeySource} 가 {@code "team"} 이 아니라
	 * {@code "reverse_lookup"} 등으로 들어올 수 있다. 이 때도 팀 키이면 {@code teamApiKeyId}/{@code teamId}
	 * 가 채워지므로, {@code apiKeySource} 만으로 판정하지 않고 페이로드의 team 식별자가 있으면 우선 TEAM 으로
	 * 본다. 그렇지 않은 경우에만 {@code apiKeySource == "team"} 폴백을 적용하고, 최종적으로 PERSONAL 로 떨어진다.</p>
	 */
	static ResolvedScope resolveScope(UsageRecordedEvent event) {
		String teamApiKeyId = trimToNull(event.teamApiKeyId());
		String teamId = trimToNull(event.teamId());
		if (teamApiKeyId != null || teamId != null || isTeamSource(event.apiKeySource())) {
			String scopeId = teamId != null ? teamId : teamApiKeyId;
			if (scopeId == null) {
				return null;
			}
			return new ResolvedScope("TEAM", scopeId);
		}
		String userId = trimToNull(event.userId());
		if (userId == null) {
			return null;
		}
		return new ResolvedScope("PERSONAL", userId);
	}

	private static boolean isTeamSource(String apiKeySource) {
		if (apiKeySource == null) {
			return false;
		}
		return "team".equals(apiKeySource.trim().toLowerCase(Locale.ROOT));
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	record ResolvedScope(String scopeType, String scopeId) {
	}

	private static Long resolveReasoningTokens(TokenUsage tokenUsage) {
		if (tokenUsage.completionReasoningTokens() == null || tokenUsage.completionReasoningTokens() < 0) {
			return 0L;
		}
		return tokenUsage.completionReasoningTokens();
	}

	private static Long resolveOutputTokens(AiProvider provider, TokenUsage tokenUsage) {
		Long completionTokens = tokenUsage.completionTokens();
		if (completionTokens == null || completionTokens < 0) {
			return 0L;
		}
		Long reasoningTokens = resolveReasoningTokens(tokenUsage);
		if (provider != AiProvider.OPENAI) {
			return completionTokens;
		}
		long pureOutput = completionTokens - reasoningTokens;
		return Math.max(pureOutput, 0L);
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
