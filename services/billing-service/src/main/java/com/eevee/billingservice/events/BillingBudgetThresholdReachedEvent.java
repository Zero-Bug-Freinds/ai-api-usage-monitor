package com.eevee.billingservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BillingBudgetThresholdReachedEvent(
        int schemaVersion,
        Instant occurredAt,
        LocalDate monthStart,
        BigDecimal thresholdPct,
        BigDecimal monthlyTotalUsd,
        BigDecimal monthlyBudgetUsd,
        String apiKeyAlias
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
}

