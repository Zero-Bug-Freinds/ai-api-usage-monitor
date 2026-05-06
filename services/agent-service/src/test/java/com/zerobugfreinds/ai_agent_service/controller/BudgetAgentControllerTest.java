package com.zerobugfreinds.ai_agent_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastResponse;
import com.zerobugfreinds.ai_agent_service.service.BudgetForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BudgetAgentControllerTest {

	private MockMvc mockMvc;

	private BudgetForecastService budgetForecastService;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		budgetForecastService = mock(BudgetForecastService.class);
		BudgetAgentController controller = new BudgetAgentController(budgetForecastService);
		objectMapper = new ObjectMapper().findAndRegisterModules();
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new AgentApiExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@Test
	void forecast_returnsOk_forValidRequest() throws Exception {
		when(budgetForecastService.forecast(any())).thenReturn(new BudgetForecastResponse(
				"WARNING",
				"주의",
				"예산 사용률 87.50% 기준 (80% 이상 WARNING, 100% 이상 CRITICAL), 소진-결제일 간격 4일",
				"HIGH",
				"7일 토큰 롤업 + 최근 7일 지출 배열 기반",
				LocalDate.now().plusDays(3),
				3,
				7L,
				4L,
				BigDecimal.valueOf(87.5),
				"테스트 메시지",
				List.of("조치1"),
				"이상 징후 없음",
				"현재 모델 유지 권장",
				BigDecimal.ZERO
		));

		mockMvc.perform(post("/api/v1/agents/budget-forecast-assistant")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.healthStatus").value("WARNING"))
				.andExpect(jsonPath("$.healthStatusLabel").value("주의"))
				.andExpect(jsonPath("$.confidenceLevel").value("HIGH"));
	}

	@Test
	void forecast_returnsServiceUnavailable_whenAiInferenceFailed() throws Exception {
		doThrow(new IllegalStateException("AI_INFERENCE_FAILED"))
				.when(budgetForecastService).forecast(any());

		mockMvc.perform(post("/api/v1/agents/budget-forecast-assistant")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_INFERENCE_FAILED"));
	}

	@Test
	void forecast_returnsBadRequest_whenRequiredFieldMissing() throws Exception {
		String invalidJson = """
				{
				  "monthlyBudgetUsd": 100,
				  "currentSpendUsd": 20,
				  "remainingTokens": 1000,
				  "averageDailyTokenUsage": 120,
				  "averageDailySpendUsd": 4,
				  "billingCycleEndDate": "%s"
				}
				""".formatted(LocalDate.now().plusDays(5));

		mockMvc.perform(post("/api/v1/agents/budget-forecast-assistant")
						.contentType(MediaType.APPLICATION_JSON)
						.content(invalidJson))
				.andExpect(status().isBadRequest());
	}

	@Test
	void forecast_returnsBadRequest_whenNegativeInputProvided() throws Exception {
		String invalidJson = """
				{
				  "userId": "user@test.com",
				  "monthlyBudgetUsd": -1,
				  "currentSpendUsd": 20,
				  "remainingTokens": 1000,
				  "averageDailyTokenUsage": 120,
				  "averageDailySpendUsd": 4,
				  "billingCycleEndDate": "%s"
				}
				""".formatted(LocalDate.now().plusDays(5));

		mockMvc.perform(post("/api/v1/agents/budget-forecast-assistant")
						.contentType(MediaType.APPLICATION_JSON)
						.content(invalidJson))
				.andExpect(status().isBadRequest());
	}

	private static BudgetForecastRequest validRequest() {
		return new BudgetForecastRequest(
				"user@test.com",
				null,
				1L,
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(80),
				50_000L,
				BigDecimal.valueOf(2000),
				BigDecimal.valueOf(5),
				LocalDate.now().plusDays(7),
				List.of(BigDecimal.valueOf(4), BigDecimal.valueOf(5), BigDecimal.valueOf(6), BigDecimal.valueOf(5)),
				List.of(1000L, 1100L, 1200L, 900L, 1050L, 980L, 1150L),
				List.of(new BudgetForecastRequest.ModelUsageShare("gemini-1.5-flash", BigDecimal.valueOf(70))),
				List.of(20L, 15L, 10L, 8L, 6L, 5L, 7L, 12L, 20L, 25L, 30L, 35L, 28L, 26L, 24L, 22L, 18L, 16L, 14L, 12L, 10L, 9L, 8L, 7L)
		);
	}
}
