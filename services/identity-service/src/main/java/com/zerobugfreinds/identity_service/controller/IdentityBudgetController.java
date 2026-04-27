package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.service.ExternalApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
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
		Optional<ExternalApiKeyService.UserMonthlyBudgetBreakdown> monthlyBudget =
				externalApiKeyService.resolveUserMonthlyBudgetBreakdown(userId);
		return monthlyBudget
				.map(value -> ResponseEntity.ok(BudgetResponse.from(value)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/budget")
	public ResponseEntity<BudgetResponse> getUserMonthlyBudgetByEmail(@RequestParam("email") String email) {
		Optional<ExternalApiKeyService.UserMonthlyBudgetBreakdown> monthlyBudget =
				externalApiKeyService.resolveUserMonthlyBudgetBreakdownByEmail(email);
		return monthlyBudget
				.map(value -> ResponseEntity.ok(BudgetResponse.from(value)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	public record BudgetResponse(BigDecimal monthlyBudgetUsd, List<BudgetByKeyResponse> monthlyBudgetsByKey) {
		public static BudgetResponse from(ExternalApiKeyService.UserMonthlyBudgetBreakdown breakdown) {
			List<BudgetByKeyResponse> items = breakdown.monthlyBudgetsByKey().stream()
					.map(v -> new BudgetByKeyResponse(v.externalApiKeyId(), v.provider(), v.alias(), v.monthlyBudgetUsd()))
					.toList();
			return new BudgetResponse(breakdown.monthlyBudgetUsd(), items);
		}
	}

	public record BudgetByKeyResponse(
			Long externalApiKeyId,
			String provider,
			String alias,
			BigDecimal monthlyBudgetUsd
	) {
	}
}
