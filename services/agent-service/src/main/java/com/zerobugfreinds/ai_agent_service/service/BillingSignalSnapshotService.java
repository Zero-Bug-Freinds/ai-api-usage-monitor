package com.zerobugfreinds.ai_agent_service.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.zerobugfreinds.ai_agent_service.dto.BillingCostCorrectedEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BillingSignalSnapshotService {

	private final Map<String, BillingKeySignal> byApiKeyId = new ConcurrentHashMap<>();

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

		byApiKeyId.put(
				apiKeyId,
				new BillingKeySignal(
						apiKeyId,
						userId,
						teamId,
						subjectType,
						event.estimatedCostUsd(),
						event.finalizedAt(),
						event.provider() != null ? event.provider().name() : null,
						event.model(),
						event.pricingRuleVersion()
				)
		);
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

		byApiKeyId.put(
				apiKeyId,
				new BillingKeySignal(
						apiKeyId,
						nextUserId,
						nextTeamId,
						nextSubjectType,
						correctedCost,
						nextFinalizedAt,
						nextProvider,
						nextModel,
						current != null ? current.pricingRuleVersion() : null
				)
		);
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
}
