package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;

public record UsageWindowTotals(
        BigDecimal totalCostUsd,
        long totalTokens
) {
}
