package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyUsagePoint(
        LocalDate date,
        long requestCount,
        long errorCount,
        long inputTokens,
        BigDecimal estimatedCost
) {
}