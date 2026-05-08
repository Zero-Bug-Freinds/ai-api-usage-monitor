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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BudgetForecastServiceTest {

	private GeminiAssistantService geminiAssistantService;
	private UsageRecordedTokenRollupService usageRecordedTokenRollupService;
	private UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService;
	private BudgetForecastService budgetForecastService;

	@BeforeEach
	void setUp() {
		geminiAssistantService = Mockito.mock(GeminiAssistantService.class);
		usageRecordedTokenRollupService = Mockito.mock(UsageRecordedTokenRollupService.class);
		usagePredictionSignalSnapshotService = Mockito.mock(UsagePredictionSignalSnapshotService.class);
		when(usageRecordedTokenRollupService.summarizeLastSevenDays(any(), any(), any()))
				.thenReturn(new UsageRecordedTokenRollupService.SevenDayTokenSummary(0L, 0L, 0L, null));
		when(usagePredictionSignalSnapshotService.findAll()).thenReturn(List.of());
		budgetForecastService = new BudgetForecastService(
				geminiAssistantService,
				usageRecordedTokenRollupService,
				usagePredictionSignalSnapshotService
		);
	}

	@Test
	void forecast_throws_whenAiResultMissing() {
		BudgetForecastRequest request = baseRequest(
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(80),
				1_000_000L,
				BigDecimal.valueOf(50_000),
				BigDecimal.ONE,
				List.of(BigDecimal.valueOf(4), BigDecimal.valueOf(5), BigDecimal.valueOf(6), BigDecimal.valueOf(5))
		);

		when(geminiAssistantService.inferForecast(any())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> budgetForecastService.forecast(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("AI_INFERENCE_FAILED");
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
						List.of("AI 조치 1", "AI 조치 2"),
						"새벽 시간대 이상 트래픽이 감지되었습니다.",
						"고비용 모델 요청의 60%를 flash 계열로 라우팅하세요.",
						BigDecimal.valueOf(35.50)
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
		assertThat(response.anomalySummary()).contains("이상");
		assertThat(response.routingRecommendation()).contains("라우팅");
		assertThat(response.estimatedRoutingSavingsPercent()).isEqualByComparingTo("35.50");
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
						List.of("조치 1", "조치 2"),
						"비정상 급증은 없습니다.",
						"gpt-4o-mini 사용 비중을 소폭 줄이세요.",
						BigDecimal.valueOf(12.00)
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
				List.of(BigDecimal.ONE),
				List.of(100L, 100L, 100L, 100L, 100L, 100L, 100L),
				List.of(new BudgetForecastRequest.ModelUsageShare("gpt-4o-mini", BigDecimal.valueOf(80))),
				List.of(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L)
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
				recentDailySpendUsd,
				List.of(1000L, 1100L, 1200L, 900L, 1000L, 950L, 1050L),
				List.of(new BudgetForecastRequest.ModelUsageShare("gemini-2.5-flash", BigDecimal.valueOf(65))),
				List.of(20L, 18L, 16L, 14L, 12L, 10L, 9L, 8L, 7L, 10L, 12L, 16L, 20L, 22L, 25L, 24L, 21L, 19L, 17L, 15L, 14L, 13L, 12L, 11L)
		);
	}
}
