package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;

public record HourlyUsagePoint(
        int hour,
        long requestCount,
        long errorCount,
        BigDecimal estimatedCostUsd
) {
}
