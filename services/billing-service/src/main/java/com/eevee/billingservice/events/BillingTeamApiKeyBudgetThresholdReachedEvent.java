package com.eevee.billingservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BillingTeamApiKeyBudgetThresholdReachedEvent(
        int schemaVersion,
        Instant occurredAt,
        long teamId,
        String triggerUserId,
        long teamApiKeyId,
        String provider,
        String apiKeyAlias,
        LocalDate monthStart,
        BigDecimal thresholdPct,
        BigDecimal monthlyTotalUsd,
        BigDecimal monthlyBudgetUsd
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

