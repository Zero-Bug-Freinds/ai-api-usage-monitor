package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.AiBudgetForecastResult;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastBatchRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastBatchResponse;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class BudgetForecastService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal WARNING_THRESHOLD = BigDecimal.valueOf(80);
	private static final BigDecimal CRITICAL_THRESHOLD = BigDecimal.valueOf(100);
	private static final long BILLING_GAP_CRITICAL_DAYS = 1;

	private final GeminiAssistantService geminiAssistantService;
	private final UsageRecordedTokenRollupService usageRecordedTokenRollupService;
	private final UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService;

	public BudgetForecastService(
			GeminiAssistantService geminiAssistantService,
			UsageRecordedTokenRollupService usageRecordedTokenRollupService,
			UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService
	) {
		this.geminiAssistantService = geminiAssistantService;
		this.usageRecordedTokenRollupService = usageRecordedTokenRollupService;
		this.usagePredictionSignalSnapshotService = usagePredictionSignalSnapshotService;
	}

	public BudgetForecastResponse forecast(BudgetForecastRequest request) {
		BudgetForecastRequest normalizedRequest = normalizeByUsageRollup(request);
		Optional<AiBudgetForecastResult> ai = geminiAssistantService.inferForecast(normalizedRequest);
		if (ai.isEmpty()) {
			throw new IllegalStateException("AI_INFERENCE_FAILED");
		}
		return buildForecastResponse(normalizedRequest, context.rollupApplied(), ai.get());
	}

	public BudgetForecastBatchResponse forecastBatch(BudgetForecastBatchRequest request) {
		List<NormalizedRequestContext> contexts = request.requests().stream()
				.map(this::normalizeByUsageRollup)
				.toList();
		List<BudgetForecastRequest> normalizedRequests = contexts.stream()
				.map(NormalizedRequestContext::request)
				.toList();
		Map<Long, AiBudgetForecastResult> aiByKeyId = geminiAssistantService.inferForecasts(normalizedRequests);
		List<BudgetForecastBatchResponse.Item> results = new ArrayList<>();
		for (int i = 0; i < contexts.size(); i++) {
			BudgetForecastRequest normalizedRequest = contexts.get(i).request();
			if (normalizedRequest.keyId() == null) {
				continue;
			}
			AiBudgetForecastResult aiResult = aiByKeyId.get(normalizedRequest.keyId());
			if (aiResult == null) {
				continue;
			}
			BudgetForecastResponse response = buildForecastResponse(normalizedRequest, contexts.get(i).rollupApplied(), aiResult);
			results.add(new BudgetForecastBatchResponse.Item(normalizedRequest.keyId(), response));
		}
		return new BudgetForecastBatchResponse(List.copyOf(results));
	}

	private static BudgetForecastResponse buildForecastResponse(
			BudgetForecastRequest normalizedRequest,
			boolean rollupApplied,
			AiBudgetForecastResult result
	) {
		Long daysUntilBillingCycleEnd = null;
		Long billingDateGapDays = null;
		if (normalizedRequest.billingCycleEndDate() != null) {
			daysUntilBillingCycleEnd = Math.max(
					0,
					ChronoUnit.DAYS.between(LocalDate.now(), normalizedRequest.billingCycleEndDate())
			);
			billingDateGapDays = ChronoUnit.DAYS.between(result.predictedRunOutDate(), normalizedRequest.billingCycleEndDate());
		}
		String healthStatusLabel = toHealthStatusLabel(result.healthStatus());
		String riskCriteria = buildRiskCriteria(result.budgetUtilizationPercent(), billingDateGapDays);
		ConfidenceAssessment confidenceAssessment = assessConfidence(normalizedRequest, rollupApplied);

			return new BudgetForecastResponse(
					result.healthStatus(),
					result.predictedRunOutDate(),
					result.daysUntilRunOut(),
					daysUntilBillingCycleEnd,
					billingDateGapDays,
					result.budgetUtilizationPercent(),
					result.assistantMessage(),
					result.recommendedActions()
			);
		}
		return deterministicForecast(normalizedRequest);
	}

	private BudgetForecastRequest normalizeByUsageRollup(BudgetForecastRequest request) {
		if (request.keyId() == null) {
			return request;
		}
		UsagePredictionSignalSnapshotService.UsagePredictionSignalSnapshot usagePredictionSnapshot =
				resolveUsagePredictionSnapshot(request);
		BigDecimal normalizedAverageDailySpendUsd = resolveAverageDailySpendUsd(request, usagePredictionSnapshot);
		List<BigDecimal> normalizedRecentDailySpendUsd = resolveRecentDailySpendUsd(request, usagePredictionSnapshot);
		boolean isTeamScope = request.teamId() != null && !request.teamId().isBlank();
		String scopeType = isTeamScope ? "TEAM" : "PERSONAL";
		String scopeId = isTeamScope ? request.teamId().trim() : request.userId().trim();
		UsageRecordedTokenRollupService.SevenDayTokenSummary summary =
				usageRecordedTokenRollupService.summarizeLastSevenDays(
						String.valueOf(request.keyId()),
						scopeType,
						scopeId
				);
		long observedSevenDayTokens = summary.totalInputTokens() + summary.totalOutputTokens();
		if (observedSevenDayTokens <= 0) {
			if (usagePredictionSnapshot == null) {
				return new NormalizedRequestContext(request, false);
			}
			BudgetForecastRequest normalizedWithoutRollup = new BudgetForecastRequest(
					request.userId(),
					request.teamId(),
					request.keyId(),
					request.monthlyBudgetUsd(),
					request.currentSpendUsd(),
					request.remainingTokens(),
					request.averageDailyTokenUsage(),
					normalizedAverageDailySpendUsd,
					request.billingCycleEndDate(),
					normalizedRecentDailySpendUsd,
					buildRecentDailyTokenUsage7d(summary, request),
					normalizeModelUsageDistribution(request.modelUsageDistribution7d()),
					normalizeHourlyTokenUsage24h(request.hourlyTokenUsage24h())
			);
			return new NormalizedRequestContext(normalizedWithoutRollup, false);
		}
		BigDecimal observedAverageDailyTokenUsage = BigDecimal.valueOf(observedSevenDayTokens)
				.divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP)
				.max(BigDecimal.ONE);
		return new BudgetForecastRequest(
				request.userId(),
				request.teamId(),
				request.keyId(),
				request.monthlyBudgetUsd(),
				request.currentSpendUsd(),
				request.remainingTokens(),
				observedAverageDailyTokenUsage,
				normalizedAverageDailySpendUsd,
				request.billingCycleEndDate(),
				normalizedRecentDailySpendUsd,
				buildRecentDailyTokenUsage7d(summary, request),
				normalizeModelUsageDistribution(request.modelUsageDistribution7d()),
				normalizeHourlyTokenUsage24h(request.hourlyTokenUsage24h())
		);
	}

	private UsagePredictionSignalSnapshotService.UsagePredictionSignalSnapshot resolveUsagePredictionSnapshot(BudgetForecastRequest request) {
		String scopeId = request.teamId() != null && !request.teamId().isBlank()
				? request.teamId().trim()
				: request.userId().trim();
		boolean teamScope = request.teamId() != null && !request.teamId().isBlank();
		return usagePredictionSignalSnapshotService.findAll().stream()
				.filter(snapshot -> teamScope
						? Objects.equals(scopeId, normalizeId(snapshot.teamId()))
						: Objects.equals(scopeId, normalizeId(snapshot.userId())))
				.max(Comparator.comparing(
						UsagePredictionSignalSnapshotService.UsagePredictionSignalSnapshot::publishedAt,
						Comparator.nullsLast(Comparator.naturalOrder())
				))
				.orElse(null);
	}

	private static BigDecimal resolveAverageDailySpendUsd(
			BudgetForecastRequest request,
			UsagePredictionSignalSnapshotService.UsagePredictionSignalSnapshot snapshot
	) {
		if (snapshot != null && snapshot.averageDailySpendUsd7d() != null && snapshot.averageDailySpendUsd7d().signum() > 0) {
			return snapshot.averageDailySpendUsd7d();
		}
		return request.averageDailySpendUsd();
	}

	private static List<BigDecimal> resolveRecentDailySpendUsd(
			BudgetForecastRequest request,
			UsagePredictionSignalSnapshotService.UsagePredictionSignalSnapshot snapshot
	) {
		if (snapshot != null && snapshot.recentDailySpendUsd() != null && !snapshot.recentDailySpendUsd().isEmpty()) {
			return normalizeRecentDailySpend(snapshot.recentDailySpendUsd());
		}
		return normalizeRecentDailySpend(request.recentDailySpendUsd());
	}

	private static String normalizeId(String value) {
		return value == null ? "" : value.trim();
	}

	private static List<BigDecimal> normalizeRecentDailySpend(List<BigDecimal> values) {
		if (values == null) {
			return List.of();
		}

		String healthStatus = resolveHealthStatus(utilizationPercent, billingDateGapDays, spendSpike);
		List<String> actions = recommendActions(healthStatus, billingDateGapDays, spendSpike);
		String assistantMessage = deterministicAssistantMessage(healthStatus, daysUntilRunOut, billingDateGapDays);

		return new BudgetForecastResponse(
				healthStatus,
				predictedRunOutDate,
				daysUntilRunOut,
				daysUntilBillingCycleEnd,
				billingDateGapDays,
				utilizationPercent,
				assistantMessage,
				actions
		);
	}

	private static String deterministicAssistantMessage(String healthStatus, long daysUntilRunOut, Long billingDateGapDays) {
		String gapPart = billingDateGapDays != null
				? "결제일과의 차이는 " + billingDateGapDays + "일입니다."
				: "결제일과의 차이는 null (결제일 필요)입니다.";
		return "현재 상태는 " + healthStatus + "이며, 현재 추세라면 약 " + daysUntilRunOut
				+ "일 뒤 예산 또는 토큰이 소진될 수 있습니다. " + gapPart;
	}

	private static BigDecimal calculateUtilizationPercent(BigDecimal currentSpendUsd, BigDecimal monthlyBudgetUsd) {
		if (monthlyBudgetUsd.compareTo(BigDecimal.ZERO) == 0) {
			return currentSpendUsd.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(999) : BigDecimal.ZERO;
		}
		return currentSpendUsd.multiply(HUNDRED).divide(monthlyBudgetUsd, 2, RoundingMode.HALF_UP);
	}

	private static double estimateDaysByBudget(BudgetForecastRequest request) {
		BigDecimal remainingBudget = request.monthlyBudgetUsd().subtract(request.currentSpendUsd());
		if (remainingBudget.compareTo(BigDecimal.ZERO) <= 0) {
			return 0;
		}
		return remainingBudget.divide(request.averageDailySpendUsd(), 4, RoundingMode.HALF_UP).doubleValue();
	}

	private static boolean isSpendSpike(List<BigDecimal> recentDailySpendUsd) {
		if (recentDailySpendUsd == null || recentDailySpendUsd.size() < 4) {
			return false;
		}
		BigDecimal latest = recentDailySpendUsd.get(recentDailySpendUsd.size() - 1);
		BigDecimal sum = BigDecimal.ZERO;
		for (int i = 0; i < recentDailySpendUsd.size() - 1; i++) {
			sum = sum.add(recentDailySpendUsd.get(i));
		}
		BigDecimal avgWithoutLatest = sum.divide(
				BigDecimal.valueOf(recentDailySpendUsd.size() - 1),
				4,
				RoundingMode.HALF_UP
		);
		if (avgWithoutLatest.compareTo(BigDecimal.ZERO) == 0) {
			return latest.compareTo(BigDecimal.ZERO) > 0;
		}
		BigDecimal ratio = latest.divide(avgWithoutLatest, 4, RoundingMode.HALF_UP);
		return ratio.compareTo(BigDecimal.valueOf(1.5)) >= 0;
	}

	private static String resolveHealthStatus(BigDecimal utilizationPercent, Long billingDateGapDays, boolean spendSpike) {
		if (utilizationPercent.compareTo(CRITICAL_THRESHOLD) >= 0
				|| (billingDateGapDays != null && billingDateGapDays > BILLING_GAP_CRITICAL_DAYS)) {
			return "CRITICAL";
		}
		if (utilizationPercent.compareTo(WARNING_THRESHOLD) >= 0 || spendSpike) {
			return "WARNING";
		}
		return "HEALTHY";
	}

	private static List<String> recommendActions(String healthStatus, Long billingDateGapDays, boolean spendSpike) {
		List<String> actions = new ArrayList<>();
		if ("CRITICAL".equals(healthStatus)) {
			actions.add("고비용 모델 사용을 즉시 제한하세요.");
			actions.add("결제일 전 예산 증액 또는 선충전 검토가 필요합니다.");
			if (billingDateGapDays != null && billingDateGapDays >= 0) {
				actions.add("현재 추세에서는 결제일 전에 소진될 가능성이 높습니다.");
			}
		} else if ("WARNING".equals(healthStatus)) {
			actions.add("사용량 상위 모델을 저비용 모델로 일부 전환하세요.");
			actions.add("일일 사용량 상한선을 임시로 낮추는 것을 권장합니다.");
		} else {
			actions.add("현재 사용 추세가 안정적입니다.");
		}
		if (spendSpike) {
			actions.add("최근 일일 사용량 급증이 감지되었습니다. 원인 점검이 필요합니다.");
		}
		return List.copyOf(actions);
	}
}
