package com.eevee.billingservice.events;

import com.eevee.usage.events.AiProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Inbound AMQP/JSON payload for applying a manual cost correction (delta) to billing aggregates.
 * <p>
 * Wire schema (JSON field names, {@code schemaVersion} evolution) is documented in
 * {@code services/billing-service/README.md} under "Cost correction (AMQP)".
 */
public record BillingCostCorrectionAmqp(
        int schemaVersion,
        UUID correctionEventId,
        String userId,
        String apiKeyId,
        /**
         * Calendar month start (first day of month) for the monthly aggregate key; must match
         * {@code aggDate}'s month when daily dimensions are present.
         */
        LocalDate monthStartDate,
        /**
         * Cost delta applied to daily (if dimensions present) and monthly totals. May be negative.
         */
        BigDecimal deltaCostUsd,
        /**
         * Day bucket for {@code daily_expenditure_agg}; required together with {@code provider} and {@code model}.
         */
        LocalDate aggDate,
        AiProvider provider,
        String model,
        Long promptTokenDelta,
        Long completionTokenDelta,
        /**
         * Optional consumer/audit hint; billing does not require this for aggregation correctness.
         */
        BigDecimal optionalCorrectedTotalUsdForScope,
        UUID relatedUsageEventId
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public BillingCostCorrectionAmqp {
        if (correctionEventId == null) {
            throw new IllegalArgumentException("correctionEventId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (apiKeyId == null || apiKeyId.isBlank()) {
            throw new IllegalArgumentException("apiKeyId is required");
        }
        if (monthStartDate == null) {
            throw new IllegalArgumentException("monthStartDate is required");
        }
        if (deltaCostUsd == null) {
            throw new IllegalArgumentException("deltaCostUsd is required");
        }
        boolean hasDaily = aggDate != null && provider != null && model != null && !model.isBlank();
        boolean hasAnyDailyField = aggDate != null || provider != null || (model != null && !model.isBlank());
        if (hasAnyDailyField && !hasDaily) {
            throw new IllegalArgumentException("aggDate, provider, and non-blank model are required together for daily corrections");
        }
    }
}
