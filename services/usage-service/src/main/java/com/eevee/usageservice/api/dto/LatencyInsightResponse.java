package com.eevee.usageservice.api.dto;

/**
 * Compares average latency for the selected window vs the immediately preceding window of equal length.
 */
public record LatencyInsightResponse(
        Double currentAvgLatencyMs,
        Double previousAvgLatencyMs,
        Double changePercent
) {
}
