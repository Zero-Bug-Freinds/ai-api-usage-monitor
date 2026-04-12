package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by billing-service after it applies pricing to a processed {@link UsageRecordedEvent}
 * (same {@link #eventId()}). Consumers (e.g. usage-service) update their own store, e.g.
 * {@code estimated_cost}, without billing reading {@code usage_db}.
 *
 * <p><strong>Wire format</strong>: JSON object fields follow Java record names (camelCase).
 * {@link #schemaVersion()} must be present from the publisher; bump when adding breaking changes.
 * Unknown JSON properties should be ignored by consumers ({@code fail-on-unknown-properties: false}).
 *
 * <p><strong>Versioning</strong>: {@link #CURRENT_SCHEMA_VERSION} is the only supported major
 * contract revision in this module; new optional fields may be added without incrementing it
 * if consumers tolerate unknowns.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageCostFinalizedEvent(
        int schemaVersion,
        UUID eventId,
        BigDecimal estimatedCostUsd,
        Instant finalizedAt,
        String pricingRuleVersion,
        AiProvider provider,
        String model
) {
    /**
     * Supported payload revision for this record shape (increment when incompatible).
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public UsageCostFinalizedEvent {
        if (schemaVersion == 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (estimatedCostUsd == null) {
            throw new IllegalArgumentException("estimatedCostUsd is required");
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported schemaVersion: " + schemaVersion + " (expected " + CURRENT_SCHEMA_VERSION + ")");
        }
        if (finalizedAt == null) {
            finalizedAt = Instant.now();
        }
    }

    /**
     * Minimal v1 message: current schema, now as {@link #finalizedAt()}, no optional metadata.
     */
    public static UsageCostFinalizedEvent v1(UUID eventId, BigDecimal estimatedCostUsd) {
        return new UsageCostFinalizedEvent(
                CURRENT_SCHEMA_VERSION,
                eventId,
                estimatedCostUsd,
                Instant.now(),
                null,
                null,
                null);
    }
}
