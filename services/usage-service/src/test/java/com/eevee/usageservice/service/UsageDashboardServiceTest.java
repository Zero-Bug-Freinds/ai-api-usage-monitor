package com.eevee.usageservice.service;

import com.eevee.usageservice.config.UsageServiceProperties;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        service = new UsageDashboardService(analyticsJdbcRepository, logRepository, properties);
    }

    @Test
    void summary_rejectsInvertedRange() {
        assertThatThrownBy(() -> service.summary(
                "user",
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 1, 1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summary_rejectsRangeExceedingMax() {
        assertThatThrownBy(() -> service.summary(
                "user",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 20)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}