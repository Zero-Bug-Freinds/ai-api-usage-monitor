package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.service.ExternalApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/api/identity/v1/users")
public class IdentityBudgetController {

	private final ExternalApiKeyService externalApiKeyService;

	public IdentityBudgetController(ExternalApiKeyService externalApiKeyService) {
		this.externalApiKeyService = externalApiKeyService;
	}

	@GetMapping("/{userId}/budget")
	public ResponseEntity<BudgetResponse> getUserMonthlyBudget(@PathVariable("userId") Long userId) {
		Optional<BigDecimal> monthlyBudgetUsd = externalApiKeyService.resolveUserMonthlyBudgetUsd(userId);
		return monthlyBudgetUsd
				.map(value -> ResponseEntity.ok(new BudgetResponse(value)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/budget")
	public ResponseEntity<BudgetResponse> getUserMonthlyBudgetByEmail(@RequestParam("email") String email) {
		Optional<BigDecimal> monthlyBudgetUsd = externalApiKeyService.resolveUserMonthlyBudgetUsdByEmail(email);
		return monthlyBudgetUsd
				.map(value -> ResponseEntity.ok(new BudgetResponse(value)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	public record BudgetResponse(BigDecimal monthlyBudgetUsd) {
	}
}
