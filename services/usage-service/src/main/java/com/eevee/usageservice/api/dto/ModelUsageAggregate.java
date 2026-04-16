package com.eevee.usageservice.api.dto;

public record ModelUsageAggregate(
        String model,
        String provider,
        long requestCount,
        long inputTokens,
        long estimatedReasoningTokens,
        long outputTokens
) {
}