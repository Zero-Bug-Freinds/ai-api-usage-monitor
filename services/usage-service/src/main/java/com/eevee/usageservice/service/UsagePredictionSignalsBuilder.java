package com.eevee.usageservice.service;

import com.eevee.usage.events.ProviderModelUsageBreakdown;
import com.eevee.usage.events.UsagePredictionSignalsEvent;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.ProviderModelCostTokenRow;
import com.eevee.usageservice.api.dto.UsageTeamUserSlice;
import com.eevee.usageservice.api.dto.UsageWindowTotals;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class UsagePredictionSignalsBuilder {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int WINDOW_DAYS_7 = 7;
    private static final int WINDOW_DAYS_14 = 14;

    private final UsageAnalyticsJdbcRepository analyticsRepository;

    public UsagePredictionSignalsBuilder(UsageAnalyticsJdbcRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    /**
     * Builds a snapshot for one (team, user) slice. Empty when the last 14 KST days have no cost and no tokens.
     */
    public Optional<UsagePredictionSignalsEvent> build(UsageTeamUserSlice slice, LocalDate asOfKst) {
        Instant start7 = asOfKst.minusDays(WINDOW_DAYS_7 - 1).atStartOfDay(KST).toInstant();
        Instant endExclusive = asOfKst.plusDays(1).atStartOfDay(KST).toInstant();
        Instant start14 = asOfKst.minusDays(WINDOW_DAYS_14 - 1).atStartOfDay(KST).toInstant();

        String teamId = slice.teamId() != null ? slice.teamId() : "";
        String userId = slice.userId();

        UsageWindowTotals totals7 = analyticsRepository.sumCostAndTokensByTeamAndUser(
                teamId, userId, start7, endExclusive);
        UsageWindowTotals totals14 = analyticsRepository.sumCostAndTokensByTeamAndUser(
                teamId, userId, start14, endExclusive);

        if (isEffectivelyEmpty(totals14)) {
            return Optional.empty();
        }

        BigDecimal avgSpend7 = totals7.totalCostUsd().divide(
                BigDecimal.valueOf(WINDOW_DAYS_7), 10, RoundingMode.HALF_UP);
        BigDecimal avgSpend14 = totals14.totalCostUsd().divide(
                BigDecimal.valueOf(WINDOW_DAYS_14), 10, RoundingMode.HALF_UP);
        BigDecimal avgTok7 = BigDecimal.valueOf(totals7.totalTokens()).divide(
                BigDecimal.valueOf(WINDOW_DAYS_7), 10, RoundingMode.HALF_UP);
        BigDecimal avgTok14 = BigDecimal.valueOf(totals14.totalTokens()).divide(
                BigDecimal.valueOf(WINDOW_DAYS_14), 10, RoundingMode.HALF_UP);

        List<BigDecimal> recentDailySpendUsd = buildRecentDailySpendSeries(
                teamId, userId, asOfKst, start7, endExclusive);

        List<ProviderModelCostTokenRow> rows = analyticsRepository.aggregateProviderModelCostAndTokensByTeamAndUser(
                teamId, userId, start7, endExclusive);
        List<ProviderModelUsageBreakdown> breakdown = new ArrayList<>(rows.size());
        for (ProviderModelCostTokenRow row : rows) {
            breakdown.add(new ProviderModelUsageBreakdown(
                    row.provider(),
                    row.model(),
                    row.totalCostUsd(),
                    row.totalTokens()));
        }

        return Optional.of(new UsagePredictionSignalsEvent(
                UsagePredictionSignalsEvent.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                Instant.now(),
                asOfKst,
                teamId,
                userId,
                avgSpend7,
                avgSpend14,
                avgTok7,
                avgTok14,
                recentDailySpendUsd,
                null,
                null,
                breakdown
        ));
    }

    private static boolean isEffectivelyEmpty(UsageWindowTotals totals14) {
        boolean costZero = totals14.totalCostUsd().compareTo(BigDecimal.ZERO) == 0;
        return costZero && totals14.totalTokens() == 0L;
    }

    private List<BigDecimal> buildRecentDailySpendSeries(
            String teamId,
            String userId,
            LocalDate asOfKst,
            Instant windowStart7,
            Instant windowEndExclusive
    ) {
        List<DailyUsagePoint> points = analyticsRepository.aggregateDailyByTeamAndUser(
                teamId, userId, windowStart7, windowEndExclusive, null);
        Map<LocalDate, BigDecimal> costByDay = new HashMap<>();
        for (DailyUsagePoint p : points) {
            costByDay.put(p.date(), p.estimatedCost() != null ? p.estimatedCost() : BigDecimal.ZERO);
        }
        List<BigDecimal> series = new ArrayList<>(WINDOW_DAYS_7);
        for (int i = 0; i < WINDOW_DAYS_7; i++) {
            LocalDate d = asOfKst.minusDays(WINDOW_DAYS_7 - 1 - i);
            series.add(costByDay.getOrDefault(d, BigDecimal.ZERO));
        }
        return series;
    }
}
