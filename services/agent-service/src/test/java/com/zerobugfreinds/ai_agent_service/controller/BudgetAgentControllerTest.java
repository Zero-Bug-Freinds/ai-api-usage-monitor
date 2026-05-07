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
				.setValidator(validator)
				.build();
	}

	@Test
	void forecast_returnsOk_forValidRequest() throws Exception {
		when(budgetForecastService.forecast(any())).thenReturn(new BudgetForecastResponse(
				"WARNING",
				LocalDate.now().plusDays(3),
				3,
				7L,
				4L,
				BigDecimal.valueOf(87.5),
				"테스트 메시지",
				List.of("조치1")
		));

		mockMvc.perform(post("/api/v1/agents/budget-forecast-assistant")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.healthStatus").value("WARNING"));
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
				List.of(BigDecimal.valueOf(4), BigDecimal.valueOf(5), BigDecimal.valueOf(6), BigDecimal.valueOf(5))
		);
	}
}
