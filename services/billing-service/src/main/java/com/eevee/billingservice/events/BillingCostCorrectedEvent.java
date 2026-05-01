package com.eevee.billingservice.events;

import com.eevee.usage.events.AiProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Outbound event emitted after a cost correction is successfully applied and the DB transaction commits.
 * <p>
 * Wire schema is documented in {@code services/billing-service/README.md} under "Cost correction (AMQP)".
 */
public record BillingCostCorrectedEvent(
        int schemaVersion,
        Instant occurredAt,
        UUID correctionEventId,
        String userId,
        String apiKeyId,
        LocalDate monthStartDate,
        BigDecimal appliedDeltaCostUsd,
        LocalDate aggDate,
        AiProvider provider,
        String model,
        BigDecimal optionalCorrectedTotalUsdForScope,
        UUID relatedUsageEventId
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
