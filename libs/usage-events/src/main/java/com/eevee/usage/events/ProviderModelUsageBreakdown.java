package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Provider/model slice for the last 7 KST calendar days (event-relative window),
 * aligned with {@link UsagePredictionSignalsEvent}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderModelUsageBreakdown(
        String provider,
        String model,
        BigDecimal totalCostUsd,
        long totalTokens
) {
}
