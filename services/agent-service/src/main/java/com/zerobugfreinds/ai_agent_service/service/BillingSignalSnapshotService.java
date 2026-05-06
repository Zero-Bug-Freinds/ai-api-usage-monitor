package com.zerobugfreinds.ai_agent_service.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.zerobugfreinds.ai_agent_service.dto.BillingCostCorrectedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BillingSignalSnapshotService {

	private static final Logger log = LoggerFactory.getLogger(BillingSignalSnapshotService.class);
	private static final String REDIS_HASH_KEY = "ai-agent:billing-signals";

	private final Map<String, BillingKeySignal> byApiKeyId = new ConcurrentHashMap<>();
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public BillingSignalSnapshotService(
			org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider,
			ObjectMapper objectMapper
	) {
		this.redisTemplate = redisTemplateProvider.getIfAvailable();
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void restoreFromRedis() {
		if (redisTemplate == null) {
			log.info("Redis template is not available; billing signal snapshots will remain in-memory only.");
			return;
		}
		try {
			Map<Object, Object> entries = redisTemplate.opsForHash().entries(REDIS_HASH_KEY);
			for (Map.Entry<Object, Object> entry : entries.entrySet()) {
				String apiKeyId = String.valueOf(entry.getKey());
				String json = String.valueOf(entry.getValue());
				BillingKeySignal signal = objectMapper.readValue(json, BillingKeySignal.class);
				if (apiKeyId != null && !apiKeyId.isBlank() && signal != null) {
					byApiKeyId.put(apiKeyId, signal);
				}
			}
			log.info("Restored {} billing signal snapshots from Redis.", byApiKeyId.size());
		} catch (Exception ex) {
			log.warn("Failed to restore billing signal snapshots from Redis.", ex);
		}
	}

	public void upsertUsageCost(
			String apiKeyId,
			String userId,
			String teamId,
			String subjectType,
			UsageCostFinalizedEvent event
	) {
		if (apiKeyId == null || apiKeyId.isBlank()) {
			return;
		}

		BillingKeySignal signal = new BillingKeySignal(
				apiKeyId,
				userId,
				teamId,
				subjectType,
				event.estimatedCostUsd(),
				event.finalizedAt(),
				event.provider() != null ? event.provider().name() : null,
				event.model(),
				event.pricingRuleVersion()
		);
		byApiKeyId.put(apiKeyId, signal);
		persistSignal(apiKeyId, signal);
	}

	public void applyCostCorrection(
			String apiKeyId,
			String userId,
			String teamId,
			String subjectType,
			BillingCostCorrectedEvent event
	) {
		if (apiKeyId == null || apiKeyId.isBlank()) {
			return;
		}
		BillingKeySignal current = byApiKeyId.get(apiKeyId);

		BigDecimal currentCost = current != null && current.latestEstimatedCostUsd() != null
				? current.latestEstimatedCostUsd()
				: BigDecimal.ZERO;
		BigDecimal delta = event.appliedDeltaCostUsd() != null ? event.appliedDeltaCostUsd() : BigDecimal.ZERO;
		BigDecimal correctedCost = currentCost.add(delta);
		if (correctedCost.signum() < 0) {
			correctedCost = BigDecimal.ZERO;
		}

		String nextUserId = userId != null && !userId.isBlank() ? userId : (current != null ? current.userId() : null);
		String nextTeamId = teamId != null && !teamId.isBlank() ? teamId : (current != null ? current.teamId() : null);
		String nextSubjectType = subjectType != null && !subjectType.isBlank()
				? subjectType
				: (current != null ? current.subjectType() : null);
		String nextProvider = event.provider() != null && !event.provider().isBlank()
				? event.provider()
				: (current != null ? current.provider() : null);
		String nextModel = event.model() != null && !event.model().isBlank()
				? event.model()
				: (current != null ? current.model() : null);
		Instant nextFinalizedAt = event.occurredAt() != null
				? event.occurredAt()
				: (current != null ? current.latestFinalizedAt() : null);

		BillingKeySignal signal = new BillingKeySignal(
				apiKeyId,
				nextUserId,
				nextTeamId,
				nextSubjectType,
				correctedCost,
				nextFinalizedAt,
				nextProvider,
				nextModel,
				current != null ? current.pricingRuleVersion() : null
		);
		byApiKeyId.put(apiKeyId, signal);
		persistSignal(apiKeyId, signal);
	}

	public void upsertReconciledCost(
			String apiKeyId,
			String userId,
			String teamId,
			String subjectType,
			BigDecimal reconciledCostUsd,
			String provider
	) {
		if (apiKeyId == null || apiKeyId.isBlank() || reconciledCostUsd == null) {
			return;
		}
		BillingKeySignal current = byApiKeyId.get(apiKeyId);
		BigDecimal mergedCost = reconciledCostUsd;
		if (current != null && current.latestEstimatedCostUsd() != null) {
			mergedCost = current.latestEstimatedCostUsd().max(reconciledCostUsd);
		}
		BillingKeySignal signal = new BillingKeySignal(
				apiKeyId,
				hasText(userId) ? userId : (current != null ? current.userId() : null),
				hasText(teamId) ? teamId : (current != null ? current.teamId() : null),
				hasText(subjectType) ? subjectType : (current != null ? current.subjectType() : "API_KEY"),
				mergedCost,
				Instant.now(),
				hasText(provider) ? provider : (current != null ? current.provider() : null),
				current != null ? current.model() : null,
				current != null ? current.pricingRuleVersion() : null
		);
		byApiKeyId.put(apiKeyId, signal);
		persistSignal(apiKeyId, signal);
	}

	public List<BillingKeySignal> findAll() {
		return byApiKeyId.values().stream()
				.sorted(Comparator.comparing(BillingKeySignal::latestFinalizedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	public List<BillingKeySignal> findByTeamId(String teamId) {
		List<BillingKeySignal> result = new ArrayList<>();
		for (BillingKeySignal signal : byApiKeyId.values()) {
			if (teamId.equals(signal.teamId())) {
				result.add(signal);
			}
		}
		result.sort(Comparator.comparing(BillingKeySignal::latestFinalizedAt, Comparator.nullsLast(Comparator.reverseOrder())));
		return result;
	}

	public record BillingKeySignal(
			String apiKeyId,
			String userId,
			String teamId,
			String subjectType,
			BigDecimal latestEstimatedCostUsd,
			Instant latestFinalizedAt,
			String provider,
			String model,
			String pricingRuleVersion
	) {
	}

	private void persistSignal(String apiKeyId, BillingKeySignal signal) {
		if (redisTemplate == null || apiKeyId == null || apiKeyId.isBlank() || signal == null) {
			return;
		}
		try {
			String json = objectMapper.writeValueAsString(signal);
			redisTemplate.opsForHash().put(REDIS_HASH_KEY, apiKeyId, json);
		} catch (JsonProcessingException ex) {
			log.warn("Failed to serialize billing signal for apiKeyId={}", apiKeyId, ex);
		} catch (Exception ex) {
			log.warn("Failed to persist billing signal for apiKeyId={}", apiKeyId, ex);
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
