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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BudgetForecastService {

	private final GeminiAssistantService geminiAssistantService;
	private final UsageRecordedTokenRollupService usageRecordedTokenRollupService;

	public BudgetForecastService(
			GeminiAssistantService geminiAssistantService,
			UsageRecordedTokenRollupService usageRecordedTokenRollupService
	) {
		this.geminiAssistantService = geminiAssistantService;
		this.usageRecordedTokenRollupService = usageRecordedTokenRollupService;
	}

	public BudgetForecastResponse forecast(BudgetForecastRequest request) {
		NormalizedRequestContext context = normalizeByUsageRollup(request);
		BudgetForecastRequest normalizedRequest = context.request();
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
				healthStatusLabel,
				riskCriteria,
				confidenceAssessment.level(),
				confidenceAssessment.criteria(),
				result.predictedRunOutDate(),
				result.daysUntilRunOut(),
				daysUntilBillingCycleEnd,
				billingDateGapDays,
				result.budgetUtilizationPercent(),
				result.assistantMessage(),
				result.recommendedActions(),
				result.anomalySummary(),
				result.routingRecommendation(),
				result.estimatedRoutingSavingsPercent()
		);
	}

	private NormalizedRequestContext normalizeByUsageRollup(BudgetForecastRequest request) {
		if (request.keyId() == null) {
			return new NormalizedRequestContext(request, false);
		}
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
			return new NormalizedRequestContext(request, false);
		}
		BigDecimal observedAverageDailyTokenUsage = BigDecimal.valueOf(observedSevenDayTokens)
				.divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP)
				.max(BigDecimal.ONE);
		BudgetForecastRequest normalized = new BudgetForecastRequest(
				request.userId(),
				request.teamId(),
				request.keyId(),
				request.monthlyBudgetUsd(),
				request.currentSpendUsd(),
				request.remainingTokens(),
				observedAverageDailyTokenUsage,
				request.averageDailySpendUsd(),
				request.billingCycleEndDate(),
				normalizeRecentDailySpend(request.recentDailySpendUsd()),
				buildRecentDailyTokenUsage7d(summary, request),
				normalizeModelUsageDistribution(request.modelUsageDistribution7d()),
				normalizeHourlyTokenUsage24h(request.hourlyTokenUsage24h())
		);
		return new NormalizedRequestContext(normalized, true);
	}

	private static List<BigDecimal> normalizeRecentDailySpend(List<BigDecimal> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream()
				.filter(value -> value != null && value.signum() >= 0)
				.toList();
	}

	private static List<Long> buildRecentDailyTokenUsage7d(
			UsageRecordedTokenRollupService.SevenDayTokenSummary summary,
			BudgetForecastRequest request
	) {
		if (request.recentDailyTokenUsage7d() != null && !request.recentDailyTokenUsage7d().isEmpty()) {
			return request.recentDailyTokenUsage7d().stream()
					.filter(value -> value != null && value >= 0)
					.limit(7)
					.toList();
		}
		long totalTokens = Math.max(0L, summary.totalInputTokens() + summary.totalOutputTokens());
		long avg = totalTokens <= 0 ? 0L : Math.max(1L, totalTokens / 7);
		List<Long> generated = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			generated.add(avg);
		}
		return List.copyOf(generated);
	}

	private static List<BudgetForecastRequest.ModelUsageShare> normalizeModelUsageDistribution(
			List<BudgetForecastRequest.ModelUsageShare> values
	) {
		if (values == null) {
			return List.of();
		}
		return values.stream()
				.filter(value -> value != null && value.model() != null && !value.model().isBlank())
				.filter(value -> value.percentage() != null && value.percentage().signum() >= 0)
				.toList();
	}

	private static List<Long> normalizeHourlyTokenUsage24h(List<Long> values) {
		if (values == null || values.isEmpty()) {
			List<Long> empty24h = new ArrayList<>();
			for (int i = 0; i < 24; i++) {
				empty24h.add(0L);
			}
			return List.copyOf(empty24h);
		}
		return values.stream()
				.filter(value -> value != null && value >= 0)
				.limit(24)
				.toList();
	}

	private static String toHealthStatusLabel(String healthStatus) {
		if ("CRITICAL".equalsIgnoreCase(healthStatus)) {
			return "위험";
		}
		if ("WARNING".equalsIgnoreCase(healthStatus)) {
			return "주의";
		}
		return "양호";
	}

	private static String buildRiskCriteria(BigDecimal utilizationPercent, Long billingDateGapDays) {
		BigDecimal utilization = utilizationPercent == null ? BigDecimal.ZERO : utilizationPercent;
		String utilizationRule = "예산 사용률 " + utilization.setScale(2, RoundingMode.HALF_UP) + "% 기준 (80% 이상 WARNING, 100% 이상 CRITICAL)";
		if (billingDateGapDays == null) {
			return utilizationRule + ", 결제일 정보 없음";
		}
		return utilizationRule + ", 소진-결제일 간격 " + billingDateGapDays + "일";
	}

	private static ConfidenceAssessment assessConfidence(BudgetForecastRequest request, boolean rollupApplied) {
		List<BigDecimal> recentDailySpendUsd = request.recentDailySpendUsd();
		int recentDays = recentDailySpendUsd == null ? 0 : recentDailySpendUsd.size();
		if (rollupApplied && recentDays >= 7) {
			return new ConfidenceAssessment("HIGH", "7일 토큰 롤업 + 최근 7일 지출 배열 기반");
		}
		if (rollupApplied || recentDays >= 4) {
			return new ConfidenceAssessment("MEDIUM", "부분 시계열(최근 " + recentDays + "일) 또는 롤업 데이터 기반");
		}
		return new ConfidenceAssessment("LOW", "입력 데이터 기간이 짧아 추세 신뢰도가 낮음");
	}

	private record NormalizedRequestContext(
			BudgetForecastRequest request,
			boolean rollupApplied
	) {
	}

	private record ConfidenceAssessment(
			String level,
			String criteria
	) {
	}
}
