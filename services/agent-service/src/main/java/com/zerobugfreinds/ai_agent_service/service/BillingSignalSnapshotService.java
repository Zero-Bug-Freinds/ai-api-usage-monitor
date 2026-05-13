package com.zerobugfreinds.ai_agent_service.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.zerobugfreinds.ai_agent_service.dto.BillingCostCorrectedEvent;
import com.zerobugfreinds.ai_agent_service.entity.BillingSignalSnapshotEntity;
import com.zerobugfreinds.ai_agent_service.repository.BillingSignalSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class BillingSignalSnapshotService {

	private static final Logger log = LoggerFactory.getLogger(BillingSignalSnapshotService.class);

	private final BillingSignalSnapshotRepository snapshotRepository;

	public BillingSignalSnapshotService(BillingSignalSnapshotRepository snapshotRepository) {
		this.snapshotRepository = snapshotRepository;
	}

	public void upsertUsageCost(
			String apiKeyId,
			String userId,
			String teamId,
			String subjectType,
			UsageCostFinalizedEvent event
	) {
		if (apiKeyId == null || apiKeyId.isBlank()) {
			log.warn("upsertUsageCost skipped: blank apiKeyId");
			return;
		}
		String normalizedKey = apiKeyId.trim();
		if (event == null) {
			log.warn("upsertUsageCost skipped: null event for apiKeyId={}", normalizedKey);
			return;
		}
		BigDecimal incomingCost = event.estimatedCostUsd() != null ? event.estimatedCostUsd() : BigDecimal.ZERO;
		if (event.estimatedCostUsd() == null) {
			log.warn("upsertUsageCost apiKeyId={}: estimatedCostUsd was null, using 0", normalizedKey);
		}

		BillingSignalSnapshotEntity entity = snapshotRepository.findByApiKeyId(normalizedKey)
				.orElse(new BillingSignalSnapshotEntity(
						normalizedKey,
						userId,
						teamId,
						subjectType,
						incomingCost,
						event.finalizedAt(),
						event.provider() != null ? event.provider().name() : null,
						event.model(),
						event.pricingRuleVersion()
				));
		entity.setUserId(userId);
		entity.setTeamId(teamId);
		entity.setSubjectType(subjectType);
		entity.setLatestEstimatedCostUsd(incomingCost);
		BigDecimal prevAccum =
				entity.getAccumulatedCostUsd() != null ? entity.getAccumulatedCostUsd() : BigDecimal.ZERO;
		entity.setAccumulatedCostUsd(prevAccum.add(incomingCost));
		entity.setLatestFinalizedAt(event.finalizedAt());
		entity.setProvider(event.provider() != null ? event.provider().name() : null);
		entity.setModel(event.model());
		entity.setPricingRuleVersion(event.pricingRuleVersion());
		snapshotRepository.save(entity);
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
		BillingSignalSnapshotEntity current = snapshotRepository.findByApiKeyId(apiKeyId).orElse(null);

		BigDecimal currentCost = current != null && current.getLatestEstimatedCostUsd() != null
				? current.getLatestEstimatedCostUsd()
				: BigDecimal.ZERO;
		BigDecimal delta = event.appliedDeltaCostUsd() != null ? event.appliedDeltaCostUsd() : BigDecimal.ZERO;
		BigDecimal correctedCost = currentCost.add(delta);
		if (correctedCost.signum() < 0) {
			correctedCost = BigDecimal.ZERO;
		}

		BigDecimal prevAccum = current != null && current.getAccumulatedCostUsd() != null
				? current.getAccumulatedCostUsd()
				: BigDecimal.ZERO;
		BigDecimal correctedAccum = prevAccum.add(delta);
		if (correctedAccum.signum() < 0) {
			correctedAccum = BigDecimal.ZERO;
		}

		String nextUserId = userId != null && !userId.isBlank() ? userId : (current != null ? current.getUserId() : null);
		String nextTeamId = teamId != null && !teamId.isBlank() ? teamId : (current != null ? current.getTeamId() : null);
		String nextSubjectType = subjectType != null && !subjectType.isBlank()
				? subjectType
				: (current != null ? current.getSubjectType() : null);
		String nextProvider = event.provider() != null && !event.provider().isBlank()
				? event.provider()
				: (current != null ? current.getProvider() : null);
		String nextModel = event.model() != null && !event.model().isBlank()
				? event.model()
				: (current != null ? current.getModel() : null);
		Instant nextFinalizedAt = event.occurredAt() != null
				? event.occurredAt()
				: (current != null ? current.getLatestFinalizedAt() : null);

		BillingSignalSnapshotEntity entity = current != null ? current : new BillingSignalSnapshotEntity(
				apiKeyId, nextUserId, nextTeamId, nextSubjectType, correctedCost, nextFinalizedAt, nextProvider, nextModel, null
		);
		entity.setUserId(nextUserId);
		entity.setTeamId(nextTeamId);
		entity.setSubjectType(nextSubjectType);
		entity.setLatestEstimatedCostUsd(correctedCost);
		entity.setAccumulatedCostUsd(correctedAccum);
		entity.setLatestFinalizedAt(nextFinalizedAt);
		entity.setProvider(nextProvider);
		entity.setModel(nextModel);
		entity.setPricingRuleVersion(current != null ? current.getPricingRuleVersion() : null);
		snapshotRepository.save(entity);
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
		BillingSignalSnapshotEntity current = snapshotRepository.findByApiKeyId(apiKeyId).orElse(null);
		BigDecimal mergedCost = reconciledCostUsd;
		if (current != null && current.getLatestEstimatedCostUsd() != null) {
			mergedCost = current.getLatestEstimatedCostUsd().max(reconciledCostUsd);
		}
		BillingSignalSnapshotEntity entity = current != null ? current : new BillingSignalSnapshotEntity(
				apiKeyId, null, null, "API_KEY", mergedCost, Instant.now(), provider, null, null
		);
		entity.setUserId(hasText(userId) ? userId : (current != null ? current.getUserId() : null));
		entity.setTeamId(hasText(teamId) ? teamId : (current != null ? current.getTeamId() : null));
		entity.setSubjectType(hasText(subjectType) ? subjectType : (current != null ? current.getSubjectType() : "API_KEY"));
		entity.setLatestEstimatedCostUsd(mergedCost);
		entity.setLatestFinalizedAt(Instant.now());
		entity.setProvider(hasText(provider) ? provider : (current != null ? current.getProvider() : null));
		entity.setModel(current != null ? current.getModel() : null);
		entity.setPricingRuleVersion(current != null ? current.getPricingRuleVersion() : null);
		snapshotRepository.save(entity);
	}

	public List<BillingKeySignal> findAll() {
		return snapshotRepository.findAllByOrderByLatestFinalizedAtDesc().stream()
				.map(this::toSignal)
				.toList();
	}

	public List<BillingKeySignal> findByTeamId(String teamId) {
		return snapshotRepository.findByTeamIdOrderByLatestFinalizedAtDesc(teamId).stream()
				.map(this::toSignal)
				.sorted(Comparator.comparing(BillingKeySignal::latestFinalizedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	public record BillingKeySignal(
			String apiKeyId,
			String userId,
			String teamId,
			String subjectType,
			BigDecimal latestEstimatedCostUsd,
			BigDecimal accumulatedCostUsd,
			Instant latestFinalizedAt,
			String provider,
			String model,
			String pricingRuleVersion
	) {
	}

	private BillingKeySignal toSignal(BillingSignalSnapshotEntity entity) {
		return new BillingKeySignal(
				entity.getApiKeyId(),
				entity.getUserId(),
				entity.getTeamId(),
				entity.getSubjectType(),
				entity.getLatestEstimatedCostUsd(),
				entity.getAccumulatedCostUsd(),
				entity.getLatestFinalizedAt(),
				entity.getProvider(),
				entity.getModel(),
				entity.getPricingRuleVersion()
		);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
