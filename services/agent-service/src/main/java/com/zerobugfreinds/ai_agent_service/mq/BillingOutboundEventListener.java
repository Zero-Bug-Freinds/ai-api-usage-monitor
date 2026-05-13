package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.zerobugfreinds.ai_agent_service.dto.BillingCostCorrectedEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Locale;
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
			JsonNode root = objectMapper.readTree(body);
			UsageCostFinalizedEvent event = objectMapper.readerFor(UsageCostFinalizedEvent.class)
					.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
					.readValue(root);
			Map<String, String> headers = toStringHeaders(message);
			eventDebugService.record("UsageCostFinalizedEvent", headers, body);
			String apiKeyId = resolveApiKeyId(message, root);
			if (apiKeyId == null || apiKeyId.isBlank()) {
				log.error(
						"UsageCostFinalizedEvent missing apiKeyId after header and JSON payload lookup; headers={}",
						headers
				);
				throw new IllegalStateException("UsageCostFinalizedEvent missing apiKeyId");
			}
			String userId = headerAsString(message, "userId");
			String teamId = headerAsString(message, "teamId");
			String subjectType = headerAsString(message, "subjectType");
			billingSignalSnapshotService.upsertUsageCost(apiKeyId.trim(), userId, teamId, subjectType, event);
		} catch (Exception ex) {
			log.error("Failed to handle UsageCostFinalizedEvent", ex);
			throw new IllegalStateException("usage cost finalized handling failed", ex);
		}
	}

	@RabbitListener(queues = "${ai-agent.rabbit.billing-correction.queue}")
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-correction", name = "enabled", havingValue = "true", matchIfMissing = true)
	public void onBillingCostCorrected(Message message) {
		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			BillingCostCorrectedEvent event = objectMapper.readValue(body, BillingCostCorrectedEvent.class);
			Map<String, String> headers = toStringHeaders(message);
			eventDebugService.record("BillingCostCorrectedEvent", headers, body);
			String apiKeyId = event.apiKeyId() != null && !event.apiKeyId().isBlank()
					? event.apiKeyId().trim()
					: resolveApiKeyIdFromHeaders(message);
			if (apiKeyId == null || apiKeyId.isBlank()) {
				log.error(
						"BillingCostCorrectedEvent missing apiKeyId after event payload and header lookup; headers={}",
						headers
				);
				throw new IllegalStateException("BillingCostCorrectedEvent missing apiKeyId");
			}
			String userId = event.userId() != null ? event.userId() : headerAsString(message, "userId");
			String teamId = headerAsString(message, "teamId");
			String subjectType = headerAsString(message, "subjectType");
			billingSignalSnapshotService.applyCostCorrection(apiKeyId.trim(), userId, teamId, subjectType, event);
		} catch (Exception ex) {
			log.error("Failed to handle BillingCostCorrectedEvent", ex);
			throw new IllegalStateException("billing cost corrected handling failed", ex);
		}
	}

	/**
	 * Resolves AMQP headers case-insensitively and treats {@code api_key_id} style as equivalent to {@code apiKeyId}.
	 * Brokers/clients may normalize header names, which would otherwise skip {@link BillingSignalSnapshotService} upserts.
	 */
	private static String headerAsString(Message message, String logicalKey) {
		String normalizedTarget = normalizeAmqpHeaderKey(logicalKey);
		for (Map.Entry<String, Object> e : message.getMessageProperties().getHeaders().entrySet()) {
			String k = e.getKey();
			if (k == null) {
				continue;
			}
			if (normalizeAmqpHeaderKey(k).equals(normalizedTarget)) {
				Object v = e.getValue();
				if (v != null) {
					String s = String.valueOf(v).trim();
					if (!s.isEmpty()) {
						return s;
					}
				}
			}
		}
		return null;
	}

	private static String normalizeAmqpHeaderKey(String key) {
		if (key == null) {
			return "";
		}
		return key.replace("_", "").toLowerCase(Locale.ROOT);
	}

	/**
	 * Resolves key id from AMQP headers first, then optional JSON fields (for envelopes that duplicate ids in body).
	 */
	private static String resolveApiKeyId(Message message, JsonNode payload) {
		String fromHeaders = resolveApiKeyIdFromHeaders(message);
		if (fromHeaders != null) {
			return fromHeaders;
		}
		if (payload != null && payload.isObject()) {
			for (String field : new String[] {"apiKeyId", "keyId", "teamApiKeyId", "externalApiKeyId"}) {
				if (!payload.hasNonNull(field)) {
					continue;
				}
				JsonNode n = payload.get(field);
				if (n.isIntegralNumber()) {
					return String.valueOf(n.asLong());
				}
				if (n.isTextual()) {
					String t = n.asText().trim();
					if (!t.isEmpty()) {
						return t;
					}
				}
			}
		}
		return null;
	}

	private static String resolveApiKeyIdFromHeaders(Message message) {
		String apiKeyId = headerAsString(message, "apiKeyId");
		if (apiKeyId != null && !apiKeyId.isBlank()) {
			return apiKeyId.trim();
		}
		String teamApiKeyId = headerAsString(message, "teamApiKeyId");
		if (teamApiKeyId != null && !teamApiKeyId.isBlank()) {
			return teamApiKeyId.trim();
		}
		return null;
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
