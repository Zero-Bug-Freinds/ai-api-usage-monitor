package com.eevee.usageservice.service;

import com.eevee.usage.events.UsagePredictionSignalsEvent;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.ProviderModelCostTokenRow;
import com.eevee.usageservice.api.dto.UsageTeamUserSlice;
import com.eevee.usageservice.api.dto.UsageWindowTotals;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsagePredictionSignalsBuilderTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 5, 2);

    @Mock
    private UsageAnalyticsJdbcRepository analyticsRepository;

    private UsagePredictionSignalsBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new UsagePredictionSignalsBuilder(analyticsRepository);
    }

    @Test
    void build_returnsEmpty_whenFourteenDayTotalsZero() {
        var slice = new UsageTeamUserSlice("team-a", "user-1");
        when(analyticsRepository.sumCostAndTokensByTeamAndUser(
                eq("team-a"), eq("user-1"), any(Instant.class), any(Instant.class)))
                .thenReturn(new UsageWindowTotals(BigDecimal.ZERO, 0L));

        Optional<UsagePredictionSignalsEvent> out = builder.build(slice, AS_OF);

        assertThat(out).isEmpty();
    }

    @Test
    void build_mapsSevenDaySeriesOldestToNewest_andBreakdown() {
        var slice = new UsageTeamUserSlice("", "user-1");
        Instant start7 = AS_OF.minusDays(6).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();
        Instant endEx = AS_OF.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();
        Instant start14 = AS_OF.minusDays(13).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();

        when(analyticsRepository.sumCostAndTokensByTeamAndUser(eq(""), eq("user-1"), eq(start7), eq(endEx)))
                .thenReturn(new UsageWindowTotals(new BigDecimal("70.0000000000"), 700L));
        when(analyticsRepository.sumCostAndTokensByTeamAndUser(eq(""), eq("user-1"), eq(start14), eq(endEx)))
                .thenReturn(new UsageWindowTotals(new BigDecimal("140.0000000000"), 1400L));

        when(analyticsRepository.aggregateDailyByTeamAndUser(eq(""), eq("user-1"), eq(start7), eq(endEx), eq(null)))
                .thenReturn(List.of(
                        new DailyUsagePoint(AS_OF.minusDays(6), 0, 0, 0, new BigDecimal("1")),
                        new DailyUsagePoint(AS_OF, 0, 0, 0, new BigDecimal("2"))
                ));

        when(analyticsRepository.aggregateProviderModelCostAndTokensByTeamAndUser(
                eq(""), eq("user-1"), eq(start7), eq(endEx)))
                .thenReturn(List.of(
                        new ProviderModelCostTokenRow("OPENAI", "gpt-4o", new BigDecimal("3"), 30L)
                ));

        Optional<UsagePredictionSignalsEvent> out = builder.build(slice, AS_OF);

        assertThat(out).isPresent();
        UsagePredictionSignalsEvent e = out.get();
        assertThat(e.recentDailySpendUsd()).hasSize(7);
        assertThat(e.recentDailySpendUsd().get(0)).isEqualByComparingTo("1");
        assertThat(e.recentDailySpendUsd().get(6)).isEqualByComparingTo("2");
        assertThat(e.averageDailySpendUsd7d()).isEqualByComparingTo("10.0000000000");
        assertThat(e.providerModelBreakdown7d()).hasSize(1);
    }
}
