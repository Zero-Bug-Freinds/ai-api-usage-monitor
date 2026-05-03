package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.AiBudgetForecastResult;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class BudgetForecastService {

	private final GeminiAssistantService geminiAssistantService;

	public BudgetForecastService(GeminiAssistantService geminiAssistantService) {
		this.geminiAssistantService = geminiAssistantService;
	}

	public BudgetForecastResponse forecast(BudgetForecastRequest request) {
		Optional<AiBudgetForecastResult> ai = geminiAssistantService.inferForecast(request);
		if (ai.isEmpty()) {
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"AI 예측 결과를 생성하지 못했습니다. Gemini API 키 및 응답 형식을 확인해 주세요."
			);
		}

		AiBudgetForecastResult result = ai.get();
		Long daysUntilBillingCycleEnd = null;
		Long billingDateGapDays = null;
		if (request.billingCycleEndDate() != null) {
			daysUntilBillingCycleEnd = Math.max(
					0,
					ChronoUnit.DAYS.between(LocalDate.now(), request.billingCycleEndDate())
			);
			billingDateGapDays = ChronoUnit.DAYS.between(result.predictedRunOutDate(), request.billingCycleEndDate());
		}

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
}
