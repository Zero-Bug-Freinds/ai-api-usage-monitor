package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BudgetForecastServiceTest {

	private GeminiAssistantService geminiAssistantService;
	private BudgetForecastService budgetForecastService;

	@BeforeEach
	void setUp() {
		geminiAssistantService = Mockito.mock(GeminiAssistantService.class);
		when(geminiAssistantService.createMessage(any(), any(), any(Long.class), any(Long.class)))
				.thenReturn("테스트 메시지");
		budgetForecastService = new BudgetForecastService(geminiAssistantService);
	}

	@Test
	void forecast_marksWarning_on80PercentBoundary() {
		BudgetForecastRequest request = baseRequest(
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(80),
				1_000_000L,
				BigDecimal.valueOf(50_000),
				BigDecimal.ONE,
				List.of(BigDecimal.valueOf(4), BigDecimal.valueOf(5), BigDecimal.valueOf(6), BigDecimal.valueOf(5))
		);

		BudgetForecastResponse response = budgetForecastService.forecast(request);

		assertThat(response.healthStatus()).isEqualTo("WARNING");
		assertThat(response.budgetUtilizationPercent()).isEqualByComparingTo("80.00");
	}

	@Test
	void forecast_marksCritical_on100PercentBoundary() {
		BudgetForecastRequest request = baseRequest(
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(100),
				300_000L,
				BigDecimal.valueOf(20_000),
				BigDecimal.valueOf(8),
				List.of(BigDecimal.valueOf(7), BigDecimal.valueOf(8), BigDecimal.valueOf(8), BigDecimal.valueOf(8))
		);

		BudgetForecastResponse response = budgetForecastService.forecast(request);

		assertThat(response.healthStatus()).isEqualTo("CRITICAL");
		assertThat(response.daysUntilRunOut()).isEqualTo(0);
	}

	@Test
	void forecast_handlesZeroBudget_asCritical() {
		BudgetForecastRequest request = baseRequest(
				BigDecimal.ZERO,
				BigDecimal.ONE,
				200_000L,
				BigDecimal.valueOf(10_000),
				BigDecimal.valueOf(2),
				List.of(BigDecimal.valueOf(1), BigDecimal.valueOf(2), BigDecimal.valueOf(2), BigDecimal.valueOf(2))
		);

		BudgetForecastResponse response = budgetForecastService.forecast(request);

		assertThat(response.healthStatus()).isEqualTo("CRITICAL");
		assertThat(response.budgetUtilizationPercent()).isEqualByComparingTo("999");
	}

	@Test
	void forecast_detectsSpendSpike_andMarksWarning() {
		BudgetForecastRequest request = baseRequest(
				BigDecimal.valueOf(200),
				BigDecimal.valueOf(60),
				500_000L,
				BigDecimal.valueOf(25_000),
				BigDecimal.valueOf(4),
				List.of(BigDecimal.valueOf(3), BigDecimal.valueOf(3), BigDecimal.valueOf(4), BigDecimal.valueOf(9))
		);

		BudgetForecastResponse response = budgetForecastService.forecast(request);

		assertThat(response.healthStatus()).isEqualTo("WARNING");
		assertThat(response.recommendedActions())
				.anyMatch(v -> v.contains("급증"));
	}

	private static BudgetForecastRequest baseRequest(
			BigDecimal budget,
			BigDecimal spend,
			Long remainingTokens,
			BigDecimal averageDailyTokenUsage,
			BigDecimal averageDailySpendUsd,
			List<BigDecimal> recentDailySpendUsd
	) {
		return new BudgetForecastRequest(
				"user@test.com",
				budget,
				spend,
				remainingTokens,
				averageDailyTokenUsage,
				averageDailySpendUsd,
				LocalDate.now().plusDays(10),
				recentDailySpendUsd
		);
	}
}
