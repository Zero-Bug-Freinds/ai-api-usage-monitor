package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.dto.BillingBudgetThresholdReachedEvent;
import com.zerobugfreinds.ai_agent_service.service.BillingSignalSnapshotService;
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
public class BillingOutboundEventListener {

	private static final Logger log = LoggerFactory.getLogger(BillingOutboundEventListener.class);

	private final ObjectMapper objectMapper;
	private final BillingSignalSnapshotService billingSignalSnapshotService;
	private final EventDebugService eventDebugService;

	public BillingOutboundEventListener(
			ObjectMapper objectMapper,
			BillingSignalSnapshotService billingSignalSnapshotService,
			EventDebugService eventDebugService
	) {
		this.objectMapper = objectMapper;
		this.billingSignalSnapshotService = billingSignalSnapshotService;
		this.eventDebugService = eventDebugService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.billing-cost.queue}")
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-cost", name = "enabled", havingValue = "true", matchIfMissing = true)
	public void onUsageCostFinalized(Message message) {
		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			UsageCostFinalizedEvent event = objectMapper.readValue(body, UsageCostFinalizedEvent.class);
			Map<String, String> headers = toStringHeaders(message);
			// 리스너에서 한 줄 추가 예시: eventDebugService.record(eventType, headers, body);
			eventDebugService.record("UsageCostFinalizedEvent", headers, body);
			String apiKeyId = headerAsString(message, "apiKeyId");
			String userId = headerAsString(message, "userId");
			String teamId = headerAsString(message, "teamId");
			String subjectType = headerAsString(message, "subjectType");
			billingSignalSnapshotService.upsertUsageCost(apiKeyId, userId, teamId, subjectType, event);
		} catch (Exception ex) {
			log.error("Failed to handle UsageCostFinalizedEvent", ex);
			throw new IllegalStateException("usage cost finalized handling failed", ex);
		}
	}

	@RabbitListener(queues = "${ai-agent.rabbit.billing-budget.queue}")
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-budget", name = "enabled", havingValue = "true", matchIfMissing = true)
	public void onBudgetThresholdReached(Message message) {
		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			BillingBudgetThresholdReachedEvent event =
					objectMapper.readValue(body, BillingBudgetThresholdReachedEvent.class);
			Map<String, String> headers = toStringHeaders(message);
			eventDebugService.record("BillingBudgetThresholdReachedEvent", headers, body);
			String apiKeyId = headerAsString(message, "apiKeyId");
			String userId = headerAsString(message, "userId");
			String teamId = headerAsString(message, "teamId");
			String subjectType = headerAsString(message, "subjectType");
			billingSignalSnapshotService.upsertBudgetThreshold(apiKeyId, userId, teamId, subjectType, event);
		} catch (Exception ex) {
			log.error("Failed to handle BillingBudgetThresholdReachedEvent", ex);
			throw new IllegalStateException("billing budget threshold handling failed", ex);
		}
	}

	private static String headerAsString(Message message, String key) {
		Object value = message.getMessageProperties().getHeaders().get(key);
		return value != null ? String.valueOf(value) : null;
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
