package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.PolicyRecommendationRequest;
import com.zerobugfreinds.ai_agent_service.dto.PolicyRecommendationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PolicyRecommendationAgentService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal WARN_THRESHOLD_PERCENT = BigDecimal.valueOf(80);
	private static final BigDecimal BLOCK_THRESHOLD_PERCENT = BigDecimal.valueOf(100);

	public PolicyRecommendationResponse recommend(PolicyRecommendationRequest request) {
		BigDecimal monthlyBudgetUsd = request.monthlyBudgetUsd();
		BigDecimal currentSpendUsd = request.currentSpendUsd();
		BigDecimal utilizationRatePercent = calculateUtilizationPercent(currentSpendUsd, monthlyBudgetUsd);

		List<String> reasons = new ArrayList<>();
		reasons.add("예산 사용률: " + utilizationRatePercent + "%");

		String recommendationLevel;
		String recommendedAction;
		if (utilizationRatePercent.compareTo(BLOCK_THRESHOLD_PERCENT) >= 0) {
			recommendationLevel = "BLOCK";
			recommendedAction = "고비용 모델 사용 차단 및 관리자 승인 필요";
			reasons.add("월 예산을 초과했거나 동일 수준입니다.");
		} else if (utilizationRatePercent.compareTo(WARN_THRESHOLD_PERCENT) >= 0) {
			recommendationLevel = "WARN";
			recommendedAction = "저비용 모델 우선 사용 권장 및 사용자 경고";
			reasons.add("월 예산 대비 사용량이 80% 이상입니다.");
		} else {
			recommendationLevel = "ALLOW";
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

	private static BigDecimal calculateUtilizationPercent(BigDecimal spendUsd, BigDecimal budgetUsd) {
		if (budgetUsd.compareTo(BigDecimal.ZERO) == 0) {
			return spendUsd.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(999) : BigDecimal.ZERO;
		}
		return spendUsd
				.multiply(HUNDRED)
				.divide(budgetUsd, 2, RoundingMode.HALF_UP);
	}
}
