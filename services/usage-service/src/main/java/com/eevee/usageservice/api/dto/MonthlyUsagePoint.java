package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;

public record MonthlyUsagePoint(
        String yearMonth,
        long requestCount,
        long errorCount,
        long inputTokens,
        BigDecimal estimatedCost
) {
}