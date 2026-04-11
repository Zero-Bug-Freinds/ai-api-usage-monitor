package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by billing-service after pricing a usage row. Consumers: usage-service (updates
 * {@code usage_recorded_log.estimated_cost} for the matching {@link UsageRecordedEvent#eventId()}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageCostFinalizedEvent(
        UUID eventId,
        BigDecimal estimatedCost,
        String currency,
        Instant calculatedAt
) {
}
