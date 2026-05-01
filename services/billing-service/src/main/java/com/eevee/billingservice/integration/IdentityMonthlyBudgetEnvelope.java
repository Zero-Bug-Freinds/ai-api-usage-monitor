package com.eevee.billingservice.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Identity budget HTTP response snapshot (Phase A authority source).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentityMonthlyBudgetEnvelope(BigDecimal monthlyBudgetUsd, List<IdentityBudgetKeyRow> monthlyBudgetsByKey) {
    public List<IdentityBudgetKeyRow> monthlyBudgetsByKey() {
        return monthlyBudgetsByKey != null ? monthlyBudgetsByKey : List.of();
    }
}
