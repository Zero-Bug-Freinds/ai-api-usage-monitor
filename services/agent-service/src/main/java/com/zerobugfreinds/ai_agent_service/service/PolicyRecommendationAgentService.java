package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.PolicyRecommendationRequest;
import com.zerobugfreinds.ai_agent_service.dto.PolicyRecommendationResponse;
import com.zerobugfreinds.ai_agent_service.dto.OptimizationRecommendationIssuedEvent;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationAnalyzeRequest;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationConfidenceLevel;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationQueryResponse;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationLevel;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationReasonCode;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationScopeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PolicyRecommendationAgentService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal WARN_THRESHOLD_PERCENT = BigDecimal.valueOf(80);
	private static final BigDecimal BLOCK_THRESHOLD_PERCENT = BigDecimal.valueOf(100);
	private static final BigDecimal DEFAULT_CURRENT_MONTHLY_COST_USD = BigDecimal.valueOf(320.00);
	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
	private static final long HIGH_LATENCY_THRESHOLD_MS = 1100L;
	private static final String RECOMMENDATION_AVAILABLE = "RECOMMENDATION_AVAILABLE";
	private static final String RECOMMENDATION_EMPTY = "NO_RECOMMENDATION";

	private final Map<RecommendationCacheKey, RecommendationQueryResponse> recommendationStore = new ConcurrentHashMap<>();
	private final BillingSignalSnapshotService billingSignalSnapshotService;
	private final ExternalModelCatalogService externalModelCatalogService;
	private final DailyCumulativeTokenSnapshotService dailyCumulativeTokenSnapshotService;
	private final UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService;
	private final UsageRecordedTokenRollupService usageRecordedTokenRollupService;

	public PolicyRecommendationAgentService(
			BillingSignalSnapshotService billingSignalSnapshotService,
			ExternalModelCatalogService externalModelCatalogService,
			DailyCumulativeTokenSnapshotService dailyCumulativeTokenSnapshotService,
			UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService,
			UsageRecordedTokenRollupService usageRecordedTokenRollupService
	) {
		this.billingSignalSnapshotService = billingSignalSnapshotService;
		this.externalModelCatalogService = externalModelCatalogService;
		this.dailyCumulativeTokenSnapshotService = dailyCumulativeTokenSnapshotService;
		this.usagePredictionSignalSnapshotService = usagePredictionSignalSnapshotService;
		this.usageRecordedTokenRollupService = usageRecordedTokenRollupService;
	}

	public PolicyRecommendationResponse recommend(PolicyRecommendationRequest request) {
		BigDecimal monthlyBudgetUsd = request.monthlyBudgetUsd();
		BigDecimal currentSpendUsd = request.currentSpendUsd();
		BigDecimal utilizationRatePercent = calculateUtilizationPercent(currentSpendUsd, monthlyBudgetUsd);

		List<String> reasons = new ArrayList<>();
		reasons.add("예산 사용률: " + utilizationRatePercent + "%");

		RecommendationLevel recommendationLevel;
		String recommendedAction;
		if (utilizationRatePercent.compareTo(BLOCK_THRESHOLD_PERCENT) >= 0) {
			recommendationLevel = RecommendationLevel.BLOCK;
			recommendedAction = "고비용 모델 사용 차단 및 관리자 승인 필요";
			reasons.add("월 예산을 초과했거나 동일 수준입니다.");
		} else if (utilizationRatePercent.compareTo(WARN_THRESHOLD_PERCENT) >= 0) {
			recommendationLevel = RecommendationLevel.WARN;
			recommendedAction = "저비용 모델 우선 사용 권장 및 사용자 경고";
			reasons.add("월 예산 대비 사용량이 80% 이상입니다.");
		} else {
			recommendationLevel = RecommendationLevel.ALLOW;
			recommendedAction = "현재 정책 유지";
			reasons.add("예산 여유가 충분합니다.");
		}

		if (request.model() != null && !request.model().isBlank()) {
			reasons.add("요청 모델: " + request.model().trim());
		}

		return new PolicyRecommendationResponse(
				recommendationLevel,
				recommendedAction,
				utilizationRatePercent,
				List.copyOf(reasons)
		);
	}

	public OptimizationRecommendationIssuedEvent analyzeAndStore(RecommendationAnalyzeRequest request) {
		Instant endAt = Instant.now();
		Instant startAt = endAt.minus(request.windowDays(), ChronoUnit.DAYS);
		long totalRequests = Math.max(100, request.windowDays() * 140L);

		BillingSignalSnapshotService.BillingKeySignal billingSignal =
				resolveBillingSignal(request.scopeType(), request.scopeId(), request.keyId());
		UsageProfile usageProfile = buildUsageProfile(request, billingSignal);
		long totalInputTokens = usageProfile.totalInputTokens();
		long totalOutputTokens = usageProfile.totalOutputTokens();
		BigDecimal ratio = calculateRatio(totalInputTokens, totalOutputTokens);
		long averageLatencyMs = usageProfile.averageLatencyMs();
		RecommendationReasonCode reasonCode = resolveReasonCode(request.scopeType(), ratio, averageLatencyMs);
		RecommendationConfidenceLevel confidenceLevel = usageProfile.confidenceLevel();

		BigDecimal currentMonthlyCost = billingSignal != null && billingSignal.latestEstimatedCostUsd() != null
				? billingSignal.latestEstimatedCostUsd()
				: DEFAULT_CURRENT_MONTHLY_COST_USD;

		ExternalModelCatalogService.CatalogSnapshot catalogSnapshot = externalModelCatalogService.currentCatalog();
		List<CandidateCost> rankedCandidates = rankCandidates(
				currentMonthlyCost,
				totalInputTokens,
				totalOutputTokens,
				catalogSnapshot.models(),
				reasonCode == RecommendationReasonCode.HIGH_LATENCY
		);
		List<CandidateCost> topCandidates = rankedCandidates.stream().limit(3).toList();
		CandidateCost bestCandidate = topCandidates.isEmpty()
				? new CandidateCost("fallback-low-cost", currentMonthlyCost, BigDecimal.ZERO, "카탈로그 미연결 fallback")
				: topCandidates.getFirst();
		BigDecimal recommendedMonthlyCost = bestCandidate.expectedMonthlyCostUsd();
		BigDecimal estimatedSavingsPct = calculateSavingsPercent(currentMonthlyCost, recommendedMonthlyCost);
		String primaryModel = bestCandidate.modelName();
		List<String> candidateModels = topCandidates.stream().map(CandidateCost::modelName).toList();

		OptimizationRecommendationIssuedEvent event = new OptimizationRecommendationIssuedEvent(
				UUID.randomUUID().toString(),
				"OPTIMIZATION_RECOMMENDATION_ISSUED",
				"v1",
				endAt,
				"agent-service",
				new OptimizationRecommendationIssuedEvent.Tenant(request.scopeType(), request.scopeId()),
				new OptimizationRecommendationIssuedEvent.Target(request.keyId(), request.scopeType(), null, null),
				new OptimizationRecommendationIssuedEvent.AnalysisWindow(startAt, endAt, "Asia/Seoul"),
				new OptimizationRecommendationIssuedEvent.Signals(
						totalRequests,
						totalInputTokens,
						totalOutputTokens,
						ratio,
						averageLatencyMs,
						BigDecimal.valueOf(0.7)
				),
				new OptimizationRecommendationIssuedEvent.Recommendation(
						reasonCode,
						confidenceLevel,
						primaryModel,
						candidateModels,
						currentMonthlyCost,
						recommendedMonthlyCost,
						estimatedSavingsPct,
						buildReasonMessage(reasonCode) + " (단가 카탈로그: " + catalogSnapshot.source() + ")"
				),
				new OptimizationRecommendationIssuedEvent.Delivery(
						buildDedupeKey(request.scopeType(), request.scopeId(), request.keyId(), reasonCode)
				)
		);

		RecommendationQueryResponse queryResponse = new RecommendationQueryResponse(
				request.keyId(),
				request.scopeType(),
				RECOMMENDATION_AVAILABLE,
				endAt,
				new RecommendationQueryResponse.MetricsContext(
						request.windowDays(),
						totalInputTokens + totalOutputTokens,
						totalInputTokens + ":" + totalOutputTokens,
						averageLatencyMs,
						totalRequests
				),
				new RecommendationQueryResponse.RecommendationDetails(
						"모델 최적화 추천",
						reasonCode,
						buildReasonMessage(reasonCode) + " (단가 카탈로그: " + catalogSnapshot.source() + ")",
						confidenceLevel,
						confidenceLevel == RecommendationConfidenceLevel.LOW ? "추천 신뢰도가 낮아 후보군 중심으로 안내됩니다." : null,
						estimatedSavingsPct,
						topCandidates.stream()
								.map(candidate -> new RecommendationQueryResponse.CandidateModel(
										candidate.modelName(),
										candidate.diffPercentFromCurrent(),
										candidate.expectedMonthlyCostUsd(),
										candidate.keyFeature()
								))
								.toList()
				)
		);
		recommendationStore.put(new RecommendationCacheKey(request.scopeType(), request.scopeId(), request.keyId()), queryResponse);
		return event;
	}

	public RecommendationQueryResponse getRecommendation(
			RecommendationScopeType scopeType,
			String scopeId,
			String keyId
	) {
		RecommendationQueryResponse response = recommendationStore.get(new RecommendationCacheKey(scopeType, scopeId, keyId));
		if (response != null) {
			return response;
		}
		return new RecommendationQueryResponse(
				keyId,
				scopeType,
				RECOMMENDATION_EMPTY,
				Instant.now(),
				new RecommendationQueryResponse.MetricsContext(0, 0L, "0:0", 0L, 0L),
				null
		);
	}

	private static BigDecimal calculateUtilizationPercent(BigDecimal spendUsd, BigDecimal budgetUsd) {
		if (budgetUsd.compareTo(BigDecimal.ZERO) == 0) {
			return spendUsd.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(999) : BigDecimal.ZERO;
		}
		return spendUsd
				.multiply(HUNDRED)
				.divide(budgetUsd, 2, RoundingMode.HALF_UP);
	}

	private static BigDecimal calculateRatio(long inputTokens, long outputTokens) {
		if (outputTokens <= 0) {
			return BigDecimal.valueOf(inputTokens);
		}
		return BigDecimal.valueOf(inputTokens)
				.divide(BigDecimal.valueOf(outputTokens), 2, RoundingMode.HALF_UP);
	}

	private static BigDecimal calculateSavingsPercent(BigDecimal currentCost, BigDecimal recommendedCost) {
		if (currentCost.compareTo(BigDecimal.ZERO) <= 0) {
			return BigDecimal.ZERO;
		}
		BigDecimal savingsPercent = currentCost.subtract(recommendedCost)
				.multiply(HUNDRED)
				.divide(currentCost, 2, RoundingMode.HALF_UP);
		return savingsPercent.max(BigDecimal.ZERO);
	}

	private static BigDecimal calculateDiffPercent(BigDecimal currentCost, BigDecimal candidateCost) {
		if (currentCost.compareTo(BigDecimal.ZERO) <= 0) {
			return BigDecimal.ZERO;
		}
		return candidateCost.subtract(currentCost)
				.multiply(HUNDRED)
				.divide(currentCost, 2, RoundingMode.HALF_UP);
	}

	private static BigDecimal estimateMonthlyCost(
			long totalInputTokens,
			long totalOutputTokens,
			ExternalModelCatalogService.ModelPricing modelPricing
	) {
		BigDecimal inputCost = BigDecimal.valueOf(totalInputTokens)
				.divide(MILLION, 6, RoundingMode.HALF_UP)
				.multiply(modelPricing.inputPricePer1mUsd());
		BigDecimal outputCost = BigDecimal.valueOf(totalOutputTokens)
				.divide(MILLION, 6, RoundingMode.HALF_UP)
				.multiply(modelPricing.outputPricePer1mUsd());
		return inputCost.add(outputCost).setScale(2, RoundingMode.HALF_UP);
	}

	private static List<CandidateCost> rankCandidates(
			BigDecimal currentMonthlyCost,
			long totalInputTokens,
			long totalOutputTokens,
			List<ExternalModelCatalogService.ModelPricing> modelCatalog,
			boolean preferHighPerformance
	) {
		Comparator<CandidateCost> sortOrder = preferHighPerformance
				? Comparator.comparing(CandidateCost::expectedMonthlyCostUsd).reversed()
				: Comparator.comparing(CandidateCost::expectedMonthlyCostUsd);
		return modelCatalog.stream()
				.map(model -> {
					BigDecimal expectedCost = estimateMonthlyCost(totalInputTokens, totalOutputTokens, model);
					BigDecimal diffPercent = calculateDiffPercent(currentMonthlyCost, expectedCost);
					return new CandidateCost(
							model.modelName(),
							expectedCost,
							diffPercent,
							model.keyFeature()
					);
				})
				.sorted(sortOrder)
				.toList();
	}

	private static RecommendationReasonCode resolveReasonCode(
			RecommendationScopeType scopeType,
			BigDecimal inputOutputRatio,
			long averageLatencyMs
	) {
		if (averageLatencyMs >= HIGH_LATENCY_THRESHOLD_MS) {
			return RecommendationReasonCode.HIGH_LATENCY;
		}
		if (inputOutputRatio.compareTo(BigDecimal.valueOf(10)) >= 0) {
			return RecommendationReasonCode.HEAVY_INPUT_RATIO;
		}
		if (inputOutputRatio.compareTo(BigDecimal.valueOf(0.5)) <= 0) {
			return RecommendationReasonCode.HEAVY_OUTPUT_RATIO;
		}
		return scopeType == RecommendationScopeType.TEAM
				? RecommendationReasonCode.OVER_SPEC_USAGE
				: RecommendationReasonCode.BALANCED_CHAT;
	}

	private UsageProfile buildUsageProfile(
			RecommendationAnalyzeRequest request,
			BillingSignalSnapshotService.BillingKeySignal billingSignal
	) {
		UsageRecordedTokenRollupService.SevenDayTokenSummary rollupSummary =
				usageRecordedTokenRollupService.summarizeLastSevenDays(
						request.keyId(),
						request.scopeType().name(),
						request.scopeId()
				);
		if (rollupSummary.totalInputTokens() > 0 || rollupSummary.totalOutputTokens() > 0) {
			long fallbackLatency = resolvePatternProfile(request, billingSignal).avgLatencyMs();
			RecommendationConfidenceLevel confidence = rollupSummary.totalRequests() >= 5
					? RecommendationConfidenceLevel.HIGH
					: RecommendationConfidenceLevel.MEDIUM;
			return new UsageProfile(
					rollupSummary.totalInputTokens(),
					rollupSummary.totalOutputTokens(),
					fallbackLatency,
					confidence
			);
		}

		long dailyTokens = resolveDailyTokensByKey(request);
		BigDecimal averageDailyTokenUsage = resolveAverageDailyTokenUsage(request);
		long resolvedDailyTokens = dailyTokens > 0
				? dailyTokens
				: averageDailyTokenUsage.longValue();
		if (resolvedDailyTokens <= 0) {
			resolvedDailyTokens = 50_000L + positiveHash(request.keyId()) % 120_000L;
		}

		PatternProfile profile = resolvePatternProfile(request, billingSignal);
		long totalWindowTokens = resolvedDailyTokens * Math.max(1, request.windowDays());
		long totalInputTokens = Math.max(1L, Math.round(totalWindowTokens * (profile.inputSharePercent() / 100.0)));
		long totalOutputTokens = Math.max(1L, totalWindowTokens - totalInputTokens);

		RecommendationConfidenceLevel confidence = dailyTokens > 0 || averageDailyTokenUsage.compareTo(BigDecimal.ZERO) > 0
				? RecommendationConfidenceLevel.HIGH
				: RecommendationConfidenceLevel.MEDIUM;
		return new UsageProfile(totalInputTokens, totalOutputTokens, profile.avgLatencyMs(), confidence);
	}

	private long resolveDailyTokensByKey(RecommendationAnalyzeRequest request) {
		String keyId = request.keyId();
		return dailyCumulativeTokenSnapshotService.findAll().stream()
				.filter(snapshot -> Objects.equals(snapshot.apiKeyId(), keyId))
				.filter(snapshot -> matchesScope(request, snapshot.teamId(), snapshot.userId()))
				.mapToLong(DailyCumulativeTokenSnapshotService.DailyCumulativeTokenSnapshot::dailyTotalTokens)
				.max()
				.orElse(0L);
	}

	private BigDecimal resolveAverageDailyTokenUsage(RecommendationAnalyzeRequest request) {
		return usagePredictionSignalSnapshotService.findAll().stream()
				.filter(snapshot -> matchesScope(request, snapshot.teamId(), snapshot.userId()))
				.map(UsagePredictionSignalSnapshotService.UsagePredictionSignalSnapshot::averageDailyTokenUsage7d)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(BigDecimal.ZERO);
	}

	private static boolean matchesScope(
			RecommendationAnalyzeRequest request,
			String teamId,
			String userId
	) {
		String normalizedTeamId = normalizeId(teamId);
		String normalizedUserId = normalizeId(userId);
		String scopeId = normalizeId(request.scopeId());
		if (request.scopeType() == RecommendationScopeType.TEAM) {
			return scopeId.equals(normalizedTeamId);
		}
		return normalizedTeamId.isBlank() && scopeId.equals(normalizedUserId);
	}

	private static String normalizeId(String value) {
		return value == null ? "" : value.trim();
	}

	private static PatternProfile resolvePatternProfile(
			RecommendationAnalyzeRequest request,
			BillingSignalSnapshotService.BillingKeySignal billingSignal
	) {
		String model = billingSignal != null && billingSignal.model() != null ? billingSignal.model().toLowerCase() : "";
		int hashBucket = positiveHash(request.keyId()) % 3;

		if (model.contains("flash")) {
			return new PatternProfile(93, 650 + (positiveHash(request.keyId()) % 120));
		}
		if (model.contains("haiku")) {
			return new PatternProfile(78, 760 + (positiveHash(request.keyId()) % 140));
		}
		if (model.contains("sonnet") || model.contains("4o")) {
			return new PatternProfile(62, 980 + (positiveHash(request.keyId()) % 220));
		}

		if (hashBucket == 0) {
			return new PatternProfile(88, 740 + (positiveHash(request.keyId()) % 160));
		}
		if (hashBucket == 1) {
			return new PatternProfile(55, 980 + (positiveHash(request.keyId()) % 220));
		}
		return new PatternProfile(72, 820 + (positiveHash(request.keyId()) % 180));
	}

	private static int positiveHash(String value) {
		return Math.abs(Objects.hashCode(value));
	}

	private static String buildReasonMessage(RecommendationReasonCode reasonCode) {
		return switch (reasonCode) {
			case HEAVY_INPUT_RATIO -> "입력 토큰 비중이 높아 입력 단가가 낮은 모델이 유리합니다.";
			case HEAVY_OUTPUT_RATIO -> "출력 토큰 비중이 높아 출력 단가 최적화가 필요합니다.";
			case HIGH_LATENCY -> "응답 지연이 높은 패턴이 감지되어 저지연 모델 전환이 권장됩니다.";
			case OVER_SPEC_USAGE -> "현재 사용 패턴 대비 과스펙 모델 사용 비중이 높습니다.";
			case BUDGET_THRESHOLD_REACHED -> "예산 임계치 도달로 비용 최적화가 필요합니다.";
			case BUDGET_EXCEEDED -> "예산 초과 상태로 즉시 비용 절감 조치가 필요합니다.";
			case BALANCED_CHAT -> "입출력 균형형 패턴으로 저비용 범용 모델이 적합합니다.";
		};
	}

	private static String buildDedupeKey(
			RecommendationScopeType scopeType,
			String scopeId,
			String keyId,
			RecommendationReasonCode reasonCode
	) {
		return "opt-rec:%s:%s:%s:%s".formatted(scopeType.name(), scopeId, keyId, reasonCode.name());
	}

	private BillingSignalSnapshotService.BillingKeySignal resolveBillingSignal(
			RecommendationScopeType scopeType,
			String scopeId,
			String keyId
	) {
		if (billingSignalSnapshotService == null) {
			return null;
		}
		if (scopeType == RecommendationScopeType.TEAM) {
			return billingSignalSnapshotService.findByTeamId(scopeId).stream()
					.filter(signal -> keyId.equals(signal.apiKeyId()))
					.findFirst()
					.orElse(null);
		}
		return billingSignalSnapshotService.findAll().stream()
				.filter(signal -> keyId.equals(signal.apiKeyId()) && scopeId.equals(signal.userId()))
				.findFirst()
				.orElse(null);
	}

	private record RecommendationCacheKey(
			RecommendationScopeType scopeType,
			String scopeId,
			String keyId
	) {
	}

	private record CandidateCost(
			String modelName,
			BigDecimal expectedMonthlyCostUsd,
			BigDecimal diffPercentFromCurrent,
			String keyFeature
	) {
	}

	private record PatternProfile(
			int inputSharePercent,
			long avgLatencyMs
	) {
	}

	private record UsageProfile(
			long totalInputTokens,
			long totalOutputTokens,
			long averageLatencyMs,
			RecommendationConfidenceLevel confidenceLevel
	) {
	}
}
