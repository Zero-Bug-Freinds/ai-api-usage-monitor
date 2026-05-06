package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.AiBudgetForecastResult;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BudgetForecastServiceTest {

	private GeminiAssistantService geminiAssistantService;
	private UsageRecordedTokenRollupService usageRecordedTokenRollupService;
	private BudgetForecastService budgetForecastService;

	@BeforeEach
	void setUp() {
		geminiAssistantService = Mockito.mock(GeminiAssistantService.class);
		usageRecordedTokenRollupService = Mockito.mock(UsageRecordedTokenRollupService.class);
		when(usageRecordedTokenRollupService.summarizeLastSevenDays(any(), any(), any()))
				.thenReturn(new UsageRecordedTokenRollupService.SevenDayTokenSummary(0L, 0L, 0L));
		when(geminiAssistantService.inferForecast(any())).thenReturn(Optional.empty());
		budgetForecastService = new BudgetForecastService(geminiAssistantService, usageRecordedTokenRollupService);
	}

	@Test
	void forecast_fallsBackToDeterministic_whenAiResultMissing() {
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
		assertThat(response.daysUntilRunOut()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void forecast_doesNotMarkCritical_whenGapIsZeroWithVeryLowUtilization() {
		BudgetForecastRequest request = new BudgetForecastRequest(
				"user@test.com",
				null,
				1L,
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(0.01),
				0L,
				BigDecimal.valueOf(10_000),
				BigDecimal.valueOf(1),
				LocalDate.now(),
				List.of(BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.01))
		);

		BudgetForecastResponse response = budgetForecastService.forecast(request);

		assertThat(response.billingDateGapDays()).isEqualTo(0);
		assertThat(response.healthStatus()).isEqualTo("HEALTHY");
		assertThat(response.budgetUtilizationPercent()).isEqualByComparingTo("0.01");
	}

	@Test
	void forecast_usesAiForecast_whenGeminiReturnsPayload() {
		LocalDate predicted = LocalDate.now().plusDays(9);
		when(geminiAssistantService.inferForecast(any())).thenReturn(Optional.of(
				new AiBudgetForecastResult(
						predicted,
						9,
						"HEALTHY",
						BigDecimal.valueOf(40.00),
						"AI가 생성한 한 줄 요약",
						List.of("AI 조치 1", "AI 조치 2")
				)
		));

		BudgetForecastRequest request = baseRequest(
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(40),
				500_000L,
				BigDecimal.valueOf(10_000),
				BigDecimal.valueOf(3),
				List.of(BigDecimal.ONE)
		);

		BudgetForecastResponse response = budgetForecastService.forecast(request);

		assertThat(response.predictedRunOutDate()).isEqualTo(predicted);
		assertThat(response.daysUntilRunOut()).isEqualTo(9);
		assertThat(response.healthStatus()).isEqualTo("HEALTHY");
		assertThat(response.assistantMessage()).isEqualTo("AI가 생성한 한 줄 요약");
		assertThat(response.recommendedActions()).containsExactly("AI 조치 1", "AI 조치 2");
		assertThat(response.budgetUtilizationPercent()).isEqualByComparingTo("40.00");
	}

	@Test
	void forecast_withoutBillingCycleEndDate_returnsNullBillingMetrics_whenAiReturnsPayload() {
		LocalDate predicted = LocalDate.now().plusDays(5);
		when(geminiAssistantService.inferForecast(any())).thenReturn(Optional.of(
				new AiBudgetForecastResult(
						predicted,
						5,
						"WARNING",
						BigDecimal.valueOf(72.12),
						"AI 요약",
						List.of("조치 1", "조치 2")
				)
		));

		BudgetForecastRequest request = new BudgetForecastRequest(
				"user@test.com",
				null,
				1L,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				200_000L,
				BigDecimal.valueOf(10_000),
				BigDecimal.valueOf(2),
				null,
				List.of(BigDecimal.ONE)
		);

		BudgetForecastResponse response = budgetForecastService.forecast(request);

		assertThat(response.daysUntilBillingCycleEnd()).isNull();
		assertThat(response.billingDateGapDays()).isNull();
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
				null,
				1L,
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
