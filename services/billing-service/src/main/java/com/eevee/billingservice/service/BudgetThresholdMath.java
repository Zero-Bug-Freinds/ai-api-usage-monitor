package com.eevee.billingservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared percent-threshold ladder and spend/budget ratio helpers for budget notification publishers.
 */
final class BudgetThresholdMath {

    /**
     * Monthly spend / budget ratios at which one notification is emitted when first crossed (1% steps).
     * <p>
     * NOTE: This is intentionally generated from integer percents to avoid floating-point drift.
     * When switching back to 10% steps, change {@code PERCENT_STEP} to 10.
     */
    private static final int PERCENT_STEP = 1;

    static final List<BigDecimal> DEFAULT_MONTHLY_THRESHOLDS =
            buildPercentThresholdsInclusive(PERCENT_STEP, 100, PERCENT_STEP);

    private BudgetThresholdMath() {
    }

    static List<BigDecimal> buildPercentThresholdsInclusive(int fromPercent, int toPercent, int stepPercent) {
        if (fromPercent <= 0 || toPercent <= 0 || toPercent < fromPercent || stepPercent <= 0) {
            throw new IllegalArgumentException("Invalid percent threshold range");
        }
        List<BigDecimal> thresholds = new ArrayList<>((toPercent - fromPercent) / stepPercent + 1);
        for (int pct = fromPercent; pct <= toPercent; pct += stepPercent) {
            thresholds.add(BigDecimal.valueOf(pct).movePointLeft(2));
        }
        return List.copyOf(thresholds);
    }

    static boolean isCrossed(
            BigDecimal beforeTotalUsd,
            BigDecimal afterTotalUsd,
            BigDecimal budgetUsd,
            BigDecimal thresholdPct
    ) {
        BigDecimal beforePct = safeRatio(beforeTotalUsd, budgetUsd);
        BigDecimal afterPct = safeRatio(afterTotalUsd, budgetUsd);
        return beforePct.compareTo(thresholdPct) < 0 && afterPct.compareTo(thresholdPct) >= 0;
    }

    static BigDecimal safeRatio(BigDecimal total, BigDecimal budget) {
        if (total == null || budget == null || budget.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(budget, 8, RoundingMode.HALF_UP);
    }
}
