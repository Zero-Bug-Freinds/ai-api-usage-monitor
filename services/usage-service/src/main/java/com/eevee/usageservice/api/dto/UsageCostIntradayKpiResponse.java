package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Today vs yesterday same-length window (KST), for dashboard cost KPI.
 * {@code changeRatePercent} is null when the prior window total is zero (undefined ratio).
 */
public record UsageCostIntradayKpiResponse(
        LocalDate kstDate,
        Instant comparisonWindowEnd,
        BigDecimal todayEstimatedCost,
        BigDecimal yesterdaySameWindowEstimatedCost,
        BigDecimal changeRatePercent
) {
}
