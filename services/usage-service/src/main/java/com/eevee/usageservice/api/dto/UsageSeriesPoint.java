package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;

public record UsageSeriesPoint(
        String bucketLabel,
        long requestCount,
        long errorCount,
        long inputTokens,
        BigDecimal estimatedCost
) {
}
