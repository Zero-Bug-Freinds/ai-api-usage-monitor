package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class BudgetForecastService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal WARNING_THRESHOLD = BigDecimal.valueOf(80);
	private static final BigDecimal CRITICAL_THRESHOLD = BigDecimal.valueOf(100);

	private final GeminiAssistantService geminiAssistantService;

	public BudgetForecastService(GeminiAssistantService geminiAssistantService) {
		this.geminiAssistantService = geminiAssistantService;
	}

	public BudgetForecastResponse forecast(BudgetForecastRequest request) {
		BigDecimal utilizationPercent = calculateUtilizationPercent(request.currentSpendUsd(), request.monthlyBudgetUsd());
		boolean spendSpike = isSpendSpike(request.recentDailySpendUsd());

		double daysByBudget = estimateDaysByBudget(request);
		double daysByTokens = request.remainingTokens() / request.averageDailyTokenUsage().doubleValue();
		long daysUntilRunOut = Math.max(0, (long) Math.ceil(Math.min(daysByBudget, daysByTokens)));
		LocalDate predictedRunOutDate = LocalDate.now().plusDays(daysUntilRunOut);
		long daysUntilBillingCycleEnd = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), request.billingCycleEndDate()));
		long billingDateGapDays = ChronoUnit.DAYS.between(predictedRunOutDate, request.billingCycleEndDate());

		String healthStatus = resolveHealthStatus(utilizationPercent, billingDateGapDays, spendSpike);
		List<String> actions = recommendActions(healthStatus, billingDateGapDays, spendSpike);
		String assistantMessage = geminiAssistantService.createMessage(
				request,
				healthStatus,
				daysUntilRunOut,
				billingDateGapDays
		);

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

	private static String resolveHealthStatus(BigDecimal utilizationPercent, long billingDateGapDays, boolean spendSpike) {
		if (utilizationPercent.compareTo(CRITICAL_THRESHOLD) >= 0 || billingDateGapDays >= 0) {
			return "CRITICAL";
		}
		if (utilizationPercent.compareTo(WARNING_THRESHOLD) >= 0 || spendSpike) {
			return "WARNING";
		}
		return "HEALTHY";
	}

	private static List<String> recommendActions(String healthStatus, long billingDateGapDays, boolean spendSpike) {
		List<String> actions = new ArrayList<>();
		if ("CRITICAL".equals(healthStatus)) {
			actions.add("고비용 모델 사용을 즉시 제한하세요.");
			actions.add("결제일 전 예산 증액 또는 선충전 검토가 필요합니다.");
			if (billingDateGapDays >= 0) {
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
