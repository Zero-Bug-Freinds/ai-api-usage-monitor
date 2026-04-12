package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * KST "today" intraday cost vs same-length window on the previous calendar day (§2.1).
 */
public record UsageCostIntradayKpiResponse(
        BigDecimal todayEstimatedCostUsd,
        BigDecimal yesterdaySameWindowEstimatedCostUsd,
        BigDecimal changeRatePercent,
        Instant comparisonWindowEnd,
        LocalDate kstDateToday
) {
}
