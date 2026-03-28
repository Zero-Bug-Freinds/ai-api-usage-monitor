package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;

public record UsageSummaryResponse(
        long totalRequests,
        long totalErrors,
        long totalInputTokens,
        BigDecimal totalEstimatedCost
) {
}