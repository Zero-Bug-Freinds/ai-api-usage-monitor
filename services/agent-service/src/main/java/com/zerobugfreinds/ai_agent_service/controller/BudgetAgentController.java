package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastBatchRequest;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastBatchResponse;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastResponse;
import com.zerobugfreinds.ai_agent_service.service.BudgetForecastService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/budget-forecast-assistant")
public class BudgetAgentController {

	private final BudgetForecastService budgetForecastService;

	public BudgetAgentController(BudgetForecastService budgetForecastService) {
		this.budgetForecastService = budgetForecastService;
	}

	@PostMapping
	public ResponseEntity<BudgetForecastResponse> forecast(@Valid @RequestBody BudgetForecastRequest request) {
		return ResponseEntity.ok(budgetForecastService.forecast(request));
	}

	@PostMapping("/batch")
	public ResponseEntity<BudgetForecastBatchResponse> forecastBatch(@Valid @RequestBody BudgetForecastBatchRequest request) {
		return ResponseEntity.ok(budgetForecastService.forecastBatch(request));
	}
}
