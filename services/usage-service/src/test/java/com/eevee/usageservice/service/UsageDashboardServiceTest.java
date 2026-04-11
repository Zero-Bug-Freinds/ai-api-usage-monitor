package com.eevee.usageservice.service;

import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageDashboardServiceTest {

    @Mock
    private UsageAnalyticsJdbcRepository analyticsJdbcRepository;

    @Mock
    private UsageRecordedLogRepository logRepository;

    private final UsageServiceProperties properties = new UsageServiceProperties();

    private UsageDashboardService service;

    @BeforeEach
    void setUp() {
        properties.getAnalytics().setMaxRangeDays(10);
        service = new UsageDashboardService(
                analyticsJdbcRepository,
                logRepository,
                properties,
                Clock.systemUTC()
        );
    }

    @Test
    void summary_rejectsInvertedRange() {
        assertThatThrownBy(() -> service.summary(
                "user",
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 1, 1),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summary_rejectsRangeExceedingMax() {
        assertThatThrownBy(() -> service.summary(
                "user",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 20),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void costIntradayKpi_computesChangeRateWhenYesterdayNonZero() {
        Clock clock = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), ZoneOffset.UTC);
        UsageDashboardService s = new UsageDashboardService(
                analyticsJdbcRepository,
                logRepository,
                properties,
                clock
        );
        when(analyticsJdbcRepository.sumEstimatedCost(eq("u"), any(), any(), isNull()))
                .thenReturn(new BigDecimal("10.00"))
                .thenReturn(new BigDecimal("5.00"));

        var kpi = s.costIntradayKpi("u", null);

        assertThat(kpi.todayEstimatedCost()).isEqualByComparingTo("10.00");
        assertThat(kpi.yesterdaySameWindowEstimatedCost()).isEqualByComparingTo("5.00");
        assertThat(kpi.changeRatePercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void listLogApiKeys_mapsDistinctIdsFromRepository() {
        when(logRepository.findDistinctApiKeyIdsByUserIdAndProvider(eq("u"), isNull()))
                .thenReturn(List.of("key-a", "key-b"));

        List<UsageLogApiKeyItemResponse> rows = service.listLogApiKeys("u", null);

        assertThat(rows).extracting(UsageLogApiKeyItemResponse::apiKeyId).containsExactly("key-a", "key-b");
    }

    @Test
    void costIntradayKpi_nullChangeRateWhenYesterdayZero() {
        Clock clock = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), ZoneOffset.UTC);
        UsageDashboardService s = new UsageDashboardService(
                analyticsJdbcRepository,
                logRepository,
                properties,
                clock
        );
        when(analyticsJdbcRepository.sumEstimatedCost(eq("u"), any(), any(), isNull()))
                .thenReturn(new BigDecimal("3.00"))
                .thenReturn(BigDecimal.ZERO);

        var kpi = s.costIntradayKpi("u", null);

        assertThat(kpi.changeRatePercent()).isNull();
    }
}
