package com.eevee.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Effective monthly budget (USD) for the authenticated user, with Phase A Identity-only provenance.
 */
public record MonthlyBudgetAuthorityResponse(
        BudgetAuthorityScope scope,
        LocalDate month,
        BigDecimal effectiveMonthlyBudgetUsd,
        BigDecimal identityUserTotalUsd,
        BigDecimal identityKeyUsd,
        List<String> resolutionNotes,
        Instant computedAt
) {
}
