package com.eevee.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TeamApiKeyMonthSpendResponse(
        long teamId,
        LocalDate monthStartDate,
        BigDecimal teamMonthlyBudgetUsd,
        BigDecimal teamMonthSpendUsd,
        List<TeamApiKeyMonthSpend> keys
) {
}

