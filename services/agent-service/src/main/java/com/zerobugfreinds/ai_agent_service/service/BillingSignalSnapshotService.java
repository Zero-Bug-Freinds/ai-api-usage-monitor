package com.zerobugfreinds.ai_agent_service.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.zerobugfreinds.ai_agent_service.dto.BillingBudgetThresholdReachedEvent;
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
		BillingKeySignal current = byApiKeyId.get(apiKeyId);
		BudgetThresholdSnapshot threshold = current != null ? current.budgetThreshold() : null;

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
						event.pricingRuleVersion(),
						threshold
				)
		);
	}

	public void upsertBudgetThreshold(
			String apiKeyId,
			String userId,
			String teamId,
			String subjectType,
			BillingBudgetThresholdReachedEvent event
	) {
		if (apiKeyId == null || apiKeyId.isBlank()) {
			return;
		}
		BillingKeySignal current = byApiKeyId.get(apiKeyId);
		BudgetThresholdSnapshot threshold = new BudgetThresholdSnapshot(
				event.thresholdPct(),
				event.monthlyTotalUsd(),
				event.monthlyBudgetUsd(),
				event.monthStart(),
				event.occurredAt()
		);

		byApiKeyId.put(
				apiKeyId,
				new BillingKeySignal(
						apiKeyId,
						userId,
						teamId,
						subjectType,
						current != null ? current.latestEstimatedCostUsd() : null,
						current != null ? current.latestFinalizedAt() : null,
						current != null ? current.provider() : null,
						current != null ? current.model() : null,
						current != null ? current.pricingRuleVersion() : null,
						threshold
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
			String pricingRuleVersion,
			BudgetThresholdSnapshot budgetThreshold
	) {
	}

	public record BudgetThresholdSnapshot(
			BigDecimal thresholdPct,
			BigDecimal monthlyTotalUsd,
			BigDecimal monthlyBudgetUsd,
			java.time.LocalDate monthStart,
			Instant occurredAt
	) {
	}
}
