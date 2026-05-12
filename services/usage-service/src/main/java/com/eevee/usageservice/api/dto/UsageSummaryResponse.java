package com.eevee.usageservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageSummaryResponse(
        long totalRequests,
        long totalErrors,
        long totalInputTokens,
        BigDecimal totalEstimatedCost,
        Double avgLatencyMs
) {
    public UsageSummaryResponse(long totalRequests, long totalErrors, long totalInputTokens, BigDecimal totalEstimatedCost) {
        this(totalRequests, totalErrors, totalInputTokens, totalEstimatedCost, null);
    }
}