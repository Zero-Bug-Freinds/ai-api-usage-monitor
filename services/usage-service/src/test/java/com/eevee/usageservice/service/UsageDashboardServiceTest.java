package com.eevee.usageservice.service;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}