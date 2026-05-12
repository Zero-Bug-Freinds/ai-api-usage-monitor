package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.UsageDataContext;
import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.api.dto.UsageSeriesUnit;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageDashboardServiceTest {

    @Mock
    private UsageAnalyticsJdbcRepository analyticsJdbcRepository;

    @Mock
    private UsageRecordedLogRepository logRepository;

    @Mock
    private ApiKeyMetadataRepository apiKeyMetadataRepository;

    private final UsageServiceProperties properties = new UsageServiceProperties();

    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-06-15T08:30:00Z"), ZoneOffset.UTC);

    private UsageDashboardService service;

    @BeforeEach
    void setUp() {
        properties.getAnalytics().setMaxRangeDays(10);
        service = new UsageDashboardService(
                analyticsJdbcRepository,
                logRepository,
                apiKeyMetadataRepository,
                properties,
                fixedClock,
                new ObjectMapper());
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
                eq(UsageDataContext.PERSONAL),
                isNull()))
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
                eq(UsageDataContext.PERSONAL),
                isNull());
    }

    @Test
    void logApiKeys_personal_loadsFromMetadata_orderedByUpdatedAtDesc() {
        ApiKeyMetadataEntity newer = ApiKeyMetadataEntity.createPersonal("2", "u1");
        newer.apply(null, "OPENAI", "beta", ApiKeyStatus.ACTIVE, Instant.parse("2025-06-20T00:00:00Z"));
        ApiKeyMetadataEntity older = ApiKeyMetadataEntity.createPersonal("1", "u1");
        older.apply(null, "OPENAI", "alpha", ApiKeyStatus.ACTIVE, Instant.parse("2025-06-10T00:00:00Z"));
        when(apiKeyMetadataRepository.findPersonalKeysForDashboard("u1", "openai")).thenReturn(List.of(newer, older));
        when(logRepository.findDistinctApiKeysForUserPersonalInRange(eq("u1"), any(), any(), eq(AiProvider.OPENAI)))
                .thenReturn(List.of());

        var keys = service.logApiKeys("u1", AiProvider.OPENAI, UsageDataContext.PERSONAL);

        assertThat(keys).hasSize(2);
        assertThat(keys.getFirst().apiKeyId()).isEqualTo("2");
        assertThat(keys.getFirst().alias()).isEqualTo("beta");
        verify(apiKeyMetadataRepository).findPersonalKeysForDashboard("u1", "openai");
        verify(logRepository).findDistinctApiKeysForUserPersonalInRange(eq("u1"), any(), any(), eq(AiProvider.OPENAI));
    }

    @Test
    void logApiKeys_personal_mergesMetadataForAlternateSubjectWhenPrimaryHasNoRows() {
        ApiKeyMetadataEntity fromAlt = ApiKeyMetadataEntity.createPersonal("k9", "sub-9");
        fromAlt.apply(null, "OPENAI", "from-alt", ApiKeyStatus.ACTIVE, Instant.parse("2025-06-01T00:00:00Z"));
        when(apiKeyMetadataRepository.findPersonalKeysForDashboard("a@b.com", "openai")).thenReturn(List.of());
        when(apiKeyMetadataRepository.findPersonalKeysForDashboard("sub-9", "openai")).thenReturn(List.of(fromAlt));
        when(logRepository.findDistinctApiKeysForUserPersonalInRange(any(), any(), any(), eq(AiProvider.OPENAI)))
                .thenReturn(List.of());

        var keys = service.logApiKeys("a@b.com", "sub-9", AiProvider.OPENAI, UsageDataContext.PERSONAL);

        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst().apiKeyId()).isEqualTo("k9");
        assertThat(keys.getFirst().alias()).isEqualTo("from-alt");
        verify(apiKeyMetadataRepository).findPersonalKeysForDashboard("a@b.com", "openai");
        verify(apiKeyMetadataRepository).findPersonalKeysForDashboard("sub-9", "openai");
    }

    @Test
    void logApiKeys_personal_skipsSecondMetadataQueryWhenAlternateEqualsPrimary() {
        when(apiKeyMetadataRepository.findPersonalKeysForDashboard("u1", null)).thenReturn(List.of());
        when(logRepository.findDistinctApiKeysForUserPersonalInRange(eq("u1"), any(), any(), isNull())).thenReturn(List.of());
        service.logApiKeys("u1", "u1", null, UsageDataContext.PERSONAL);
        verify(apiKeyMetadataRepository, times(1)).findPersonalKeysForDashboard("u1", null);
    }

    @Test
    void logApiKeys_personal_mergesDistinctLogKeysWhenMetadataEmpty() {
        when(apiKeyMetadataRepository.findPersonalKeysForDashboard("u1", null)).thenReturn(List.of());
        when(logRepository.findDistinctApiKeysForUserPersonalInRange(eq("u1"), any(), any(), isNull()))
                .thenReturn(List.of(new UsageLogApiKeyItemResponse("k1", "from-logs", ApiKeyStatus.ACTIVE)));

        var keys = service.logApiKeys("u1", null, null, UsageDataContext.PERSONAL);

        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst().apiKeyId()).isEqualTo("k1");
        assertThat(keys.getFirst().alias()).isEqualTo("from-logs");
        verify(logRepository).findDistinctApiKeysForUserPersonalInRange(eq("u1"), any(), any(), isNull());
    }

    @Test
    void logApiKeys_team_stillUsesUsageLogsDistinct() {
        when(logRepository.findDistinctApiKeysForUserTeamMemberInRange(eq("u1"), any(), any(), isNull()))
                .thenReturn(List.of());
        service.logApiKeys("u1", null, UsageDataContext.TEAM_MEMBER_ONLY);
        verify(logRepository).findDistinctApiKeysForUserTeamMemberInRange(eq("u1"), any(), any(), isNull());
        verify(apiKeyMetadataRepository, never()).findPersonalKeysForDashboard(any(), any());
    }
}
