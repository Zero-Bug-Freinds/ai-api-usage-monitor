package com.eevee.usageservice.service;

import com.eevee.usageservice.api.dto.UsageDataContext;
import com.eevee.usageservice.api.dto.UsageSeriesUnit;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageDashboardServiceTest {

    @Mock
    private UsageAnalyticsJdbcRepository analyticsJdbcRepository;

    @Mock
    private UsageRecordedLogRepository logRepository;

    private final UsageServiceProperties properties = new UsageServiceProperties();

    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-06-15T08:30:00Z"), ZoneOffset.UTC);

    private UsageDashboardService service;

    @BeforeEach
    void setUp() {
        properties.getAnalytics().setMaxRangeDays(10);
        service = new UsageDashboardService(analyticsJdbcRepository, logRepository, properties, fixedClock, new ObjectMapper());
    }

    @Test
    void summary_rejectsInvertedRange() {
        assertThatThrownBy(() -> {
            service.summary(
                    "user",
                    LocalDate.of(2025, 2, 1),
                    LocalDate.of(2025, 1, 1),
                    null
            );
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summary_rejectsRangeExceedingMax() {
        assertThatThrownBy(() -> {
            service.summary(
                    "user",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 1, 20),
                    null
            );
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void latencyStabilitySeries_hour_requiresSingleDayRange() {
        assertThatThrownBy(() -> {
            service.latencyStabilitySeries(
                    "user-1",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 1, 2),
                    null,
                    UsageSeriesUnit.HOUR,
                    UsageDataContext.PERSONAL,
                    null
            );
        }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("single-day");
    }

    @Test
    void latencyInsight_comparesCurrentRangeToPreviousWindow() {
        when(analyticsJdbcRepository.aggregateAvgLatencyMsForUserFromLogs(
                eq("user-1"),
                any(),
                any(),
                isNull(),
                eq(""),
                eq(UsageDataContext.PERSONAL)))
                .thenReturn(100.0, 80.0);

        var insight = service.latencyInsight(
                "user-1",
                LocalDate.of(2025, 6, 10),
                LocalDate.of(2025, 6, 12),
                null,
                UsageDataContext.PERSONAL,
                null);

        assertThat(insight.currentAvgLatencyMs()).isEqualTo(100.0);
        assertThat(insight.previousAvgLatencyMs()).isEqualTo(80.0);
        assertThat(insight.changePercent()).isCloseTo(25.0, within(0.01));

        verify(analyticsJdbcRepository, times(2)).aggregateAvgLatencyMsForUserFromLogs(
                eq("user-1"),
                any(),
                any(),
                isNull(),
                eq(""),
                eq(UsageDataContext.PERSONAL));
    }
}
