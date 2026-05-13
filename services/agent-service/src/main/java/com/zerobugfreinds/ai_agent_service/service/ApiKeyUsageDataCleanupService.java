package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.repository.BillingSignalSnapshotRepository;
import com.zerobugfreinds.ai_agent_service.repository.DailyCumulativeTokenSnapshotRepository;
import com.zerobugfreinds.ai_agent_service.repository.RecommendationSnapshotRepository;
import com.zerobugfreinds.ai_agent_service.repository.UsageRecordedTokenRollupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyUsageDataCleanupService {

	private static final Logger log = LoggerFactory.getLogger(ApiKeyUsageDataCleanupService.class);

	private final BillingSignalSnapshotRepository billingSignalSnapshotRepository;
	private final UsageRecordedTokenRollupRepository usageRecordedTokenRollupRepository;
	private final DailyCumulativeTokenSnapshotRepository dailyCumulativeTokenSnapshotRepository;
	private final RecommendationSnapshotRepository recommendationSnapshotRepository;

	public ApiKeyUsageDataCleanupService(
			BillingSignalSnapshotRepository billingSignalSnapshotRepository,
			UsageRecordedTokenRollupRepository usageRecordedTokenRollupRepository,
			DailyCumulativeTokenSnapshotRepository dailyCumulativeTokenSnapshotRepository,
			RecommendationSnapshotRepository recommendationSnapshotRepository
	) {
		this.billingSignalSnapshotRepository = billingSignalSnapshotRepository;
		this.usageRecordedTokenRollupRepository = usageRecordedTokenRollupRepository;
		this.dailyCumulativeTokenSnapshotRepository = dailyCumulativeTokenSnapshotRepository;
		this.recommendationSnapshotRepository = recommendationSnapshotRepository;
	}

	/**
	 * Removes token rollups, daily token snapshots, and recommendation rows for a key.
	 * <p><strong>Does not</strong> delete {@code billing_signal_projection} so cumulative cost from
	 * {@code UsageCostFinalizedEvent} survives team/personal key deletion (same policy as personal external keys).
	 */
	@Transactional
	public void purgeUsageProjectionsExcludingBillingSignals(String apiKeyId) {
		if (apiKeyId == null || apiKeyId.isBlank()) {
			return;
		}
		String normalizedApiKeyId = apiKeyId.trim();
		long removedTokenRollups = usageRecordedTokenRollupRepository.deleteByKeyId(normalizedApiKeyId);
		long removedDailyTokens = dailyCumulativeTokenSnapshotRepository.deleteByApiKeyId(normalizedApiKeyId);
		long removedRecommendations = recommendationSnapshotRepository.deleteByKeyId(normalizedApiKeyId);
		log.info(
				"Purged agent usage projections (billing signals retained) for apiKeyId={} tokenRollups={} dailyTokens={} recommendations={}",
				normalizedApiKeyId,
				removedTokenRollups,
				removedDailyTokens,
				removedRecommendations
		);
	}

	/**
	 * Full purge including {@code billing_signal_projection}. Prefer
	 * {@link #purgeUsageProjectionsExcludingBillingSignals(String)} for delete flows that must keep spend history.
	 */
	@Transactional
	public void purgeByApiKeyId(String apiKeyId) {
		if (apiKeyId == null || apiKeyId.isBlank()) {
			return;
		}
		String normalizedApiKeyId = apiKeyId.trim();
		long removedBillingSignals = billingSignalSnapshotRepository.deleteByApiKeyId(normalizedApiKeyId);
		long removedTokenRollups = usageRecordedTokenRollupRepository.deleteByKeyId(normalizedApiKeyId);
		long removedDailyTokens = dailyCumulativeTokenSnapshotRepository.deleteByApiKeyId(normalizedApiKeyId);
		long removedRecommendations = recommendationSnapshotRepository.deleteByKeyId(normalizedApiKeyId);
		log.info(
				"Purged agent usage projections for apiKeyId={} billingSignals={} tokenRollups={} dailyTokens={} recommendations={}",
				normalizedApiKeyId,
				removedBillingSignals,
				removedTokenRollups,
				removedDailyTokens,
				removedRecommendations
		);
	}
}
