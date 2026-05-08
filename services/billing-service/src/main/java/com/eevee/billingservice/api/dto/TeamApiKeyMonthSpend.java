package com.eevee.billingservice.api.dto;

import java.math.BigDecimal;

public record TeamApiKeyMonthSpend(
        long teamApiKeyId,
        String alias,
        String provider,
        BigDecimal monthlyBudgetUsd,
        String status,
        BigDecimal monthSpendUsd
) {
}

