package com.eevee.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenditureSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalCostUsd,
        BigDecimal monthlyBudgetUsd
) {
}
