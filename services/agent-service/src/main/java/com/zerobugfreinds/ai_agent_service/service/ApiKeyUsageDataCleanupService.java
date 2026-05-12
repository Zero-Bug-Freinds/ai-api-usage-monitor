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
