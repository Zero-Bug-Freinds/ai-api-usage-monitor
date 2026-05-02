package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of usage-derived signals for budget / token depletion forecasting (Task36).
 * Computed in {@code usage-service} from {@code daily_usage_summary} using KST calendar boundaries,
 * consistent with dashboard analytics.
 *
 * <p><strong>Budget alignment</strong>: average windows and {@link #recentDailySpendUsd()} match the
 * semantics expected by {@code agent-service} {@code BudgetForecastRequest} when mapping:
 * use {@link #averageDailySpendUsd7d()} / {@link #averageDailyTokenUsage7d()} as the primary
 * short-horizon inputs together with {@link #recentDailySpendUsd()} (7 elements, oldest → newest).
 *
 * <p><strong>Non-usage fields</strong>: {@link #remainingTokens()} and {@link #billingCycleEndDate()}
 * are {@code null} when {@code usage-service} does not own that data; consumers should merge with
 * billing / identity sources without contradicting those systems.
 *
 * <p><strong>Wire format</strong>: JSON camelCase; {@link #schemaVersion()} must be set by the publisher.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsagePredictionSignalsEvent(
        int schemaVersion,
        UUID eventId,
        Instant publishedAt,
        LocalDate asOfDateKst,
        String teamId,
        String userId,
        BigDecimal averageDailySpendUsd7d,
        BigDecimal averageDailySpendUsd14d,
        BigDecimal averageDailyTokenUsage7d,
        BigDecimal averageDailyTokenUsage14d,
        List<BigDecimal> recentDailySpendUsd,
        Long remainingTokens,
        LocalDate billingCycleEndDate,
        List<ProviderModelUsageBreakdown> providerModelBreakdown7d
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public UsagePredictionSignalsEvent {
        if (schemaVersion == 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported schemaVersion: " + schemaVersion + " (expected " + CURRENT_SCHEMA_VERSION + ")");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
        if (asOfDateKst == null) {
            throw new IllegalArgumentException("asOfDateKst is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (teamId == null) {
            throw new IllegalArgumentException("teamId is required (use empty string for personal scope)");
        }
    }
}
