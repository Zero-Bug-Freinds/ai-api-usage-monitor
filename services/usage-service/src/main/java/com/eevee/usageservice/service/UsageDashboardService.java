package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.HourlyUsagePoint;
import com.eevee.usageservice.api.dto.LatencyInsightResponse;
import com.eevee.usageservice.api.dto.LatencyStabilityPoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.PagedLogsResponse;
import com.eevee.usageservice.api.dto.UsageCostIntradayKpiResponse;
import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.api.dto.UsageSeriesPoint;
import com.eevee.usageservice.api.dto.UsageSeriesUnit;
import com.eevee.usageservice.api.dto.UsageLogEntryResponse;
import com.eevee.usageservice.api.dto.UsageDataContext;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UsageDashboardService {

    private static final Logger log = LoggerFactory.getLogger(UsageDashboardService.class);

    /** Dashboard date ranges and buckets align with Korea Standard Time (same as identity-service convention). */
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");
    private static final String DELETED_ALIAS_SUFFIX = " (삭제)";

    private static final int LOG_API_KEY_LOOKUP_DAYS = 30;
    private static final int MAX_DASHBOARD_RANGE_DAYS = 366;

    /**
     * Optional team filter for {@link UsageDataContext#TEAM_MEMBER_ONLY} dashboard APIs; ignored for other contexts.
     */
    private static String teamMemberDashboardScope(UsageDataContext dataContext, String teamId) {
        if (dataContext != UsageDataContext.TEAM_MEMBER_ONLY || teamId == null || teamId.isBlank()) {
            return null;
        }
        return teamId.trim();
    }

    private final UsageAnalyticsJdbcRepository analyticsJdbcRepository;
    private final UsageRecordedLogRepository logRepository;
    private final ApiKeyMetadataRepository apiKeyMetadataRepository;
    private final UsageServiceProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public UsageDashboardService(
            UsageAnalyticsJdbcRepository analyticsJdbcRepository,
            UsageRecordedLogRepository logRepository,
            ApiKeyMetadataRepository apiKeyMetadataRepository,
            UsageServiceProperties properties,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.analyticsJdbcRepository = analyticsJdbcRepository;
        this.logRepository = logRepository;
        this.apiKeyMetadataRepository = apiKeyMetadataRepository;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summary(String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        return summary(userId, from, toInclusive, provider, UsageDataContext.PERSONAL, null, null);
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summary(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return summary(userId, from, toInclusive, provider, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summary(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Range r = validateRange(from, toInclusive);
        long startedAt = System.nanoTime();
        String key = normalizeApiKey(apiKeyId);
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        UsageSummaryResponse response;
        if (key != null) {
            response = analyticsJdbcRepository.aggregateSummaryForUserFromLogs(
                    userId, r.from(), r.toExclusive(), provider, key, dataContext, teamScope);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY && teamScope != null) {
            response = analyticsJdbcRepository.aggregateSummaryByTeamAndUser(
                    teamScope, userId, r.from(), r.toExclusive(), provider);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY) {
            response = analyticsJdbcRepository.aggregateSummaryTeamMemberOnly(userId, r.from(), r.toExclusive(), provider);
        } else {
            response = analyticsJdbcRepository.aggregateSummary(userId, r.from(), r.toExclusive(), provider);
        }
        log.debug("dashboard.summary dbMs={} range={}~{} provider={} dataContext={}",
                (System.nanoTime() - startedAt) / 1_000_000, from, toInclusive, provider, dataContext);
        return response;
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summaryByTeam(String teamId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateSummaryByTeam(teamId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summaryByTeam(
            String teamId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId
    ) {
        if (!restrictTeamToApiKey(apiKeyId)) {
            return summaryByTeam(teamId, from, toInclusive, provider);
        }
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateSummaryForTeamFromLogs(teamId, r.from(), r.toExclusive(), provider, apiKeyId.trim());
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summaryByTeamAndUser(String teamId, String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateSummaryByTeamAndUser(teamId, userId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<DailyUsagePoint> dailySeries(String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        return dailySeries(userId, from, toInclusive, provider, UsageDataContext.PERSONAL, null, null);
    }

    @Transactional(readOnly = true)
    public List<DailyUsagePoint> dailySeries(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return dailySeries(userId, from, toInclusive, provider, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public List<DailyUsagePoint> dailySeries(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Range r = validateRange(from, toInclusive);
        long startedAt = System.nanoTime();
        String key = normalizeApiKey(apiKeyId);
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        List<DailyUsagePoint> rows;
        if (key != null) {
            rows = analyticsJdbcRepository.aggregateDailyForUserFromLogs(
                    userId, r.from(), r.toExclusive(), provider, key, dataContext, teamScope);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY && teamScope != null) {
            rows = analyticsJdbcRepository.aggregateDailyByTeamAndUser(
                    teamScope, userId, r.from(), r.toExclusive(), provider);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY) {
            rows = analyticsJdbcRepository.aggregateDailyTeamMemberOnly(userId, r.from(), r.toExclusive(), provider);
        } else {
            rows = analyticsJdbcRepository.aggregateDaily(userId, r.from(), r.toExclusive(), provider);
        }
        log.debug("dashboard.daily dbMs={} rows={} range={}~{} provider={}", (System.nanoTime() - startedAt) / 1_000_000, rows.size(), from, toInclusive, provider);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<DailyUsagePoint> dailySeriesByTeam(String teamId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateDailyByTeam(teamId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<DailyUsagePoint> dailySeriesByTeam(
            String teamId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId
    ) {
        if (!restrictTeamToApiKey(apiKeyId)) {
            return dailySeriesByTeam(teamId, from, toInclusive, provider);
        }
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateDailyForTeamFromLogs(teamId, r.from(), r.toExclusive(), provider, apiKeyId.trim());
    }

    @Transactional(readOnly = true)
    public List<DailyUsagePoint> dailySeriesByTeamAndUser(String teamId, String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateDailyByTeamAndUser(teamId, userId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<MonthlyUsagePoint> monthlySeries(String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        return monthlySeries(userId, from, toInclusive, provider, UsageDataContext.PERSONAL, null, null);
    }

    @Transactional(readOnly = true)
    public List<MonthlyUsagePoint> monthlySeries(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return monthlySeries(userId, from, toInclusive, provider, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public List<MonthlyUsagePoint> monthlySeries(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Range r = validateRange(from, toInclusive);
        long startedAt = System.nanoTime();
        String key = normalizeApiKey(apiKeyId);
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        List<MonthlyUsagePoint> rows;
        if (key != null) {
            rows = analyticsJdbcRepository.aggregateMonthlyForUserFromLogs(
                    userId, r.from(), r.toExclusive(), provider, key, dataContext, teamScope);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY && teamScope != null) {
            rows = analyticsJdbcRepository.aggregateMonthlyByTeamAndUser(
                    teamScope, userId, r.from(), r.toExclusive(), provider);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY) {
            rows = analyticsJdbcRepository.aggregateMonthlyTeamMemberOnly(userId, r.from(), r.toExclusive(), provider);
        } else {
            rows = analyticsJdbcRepository.aggregateMonthly(userId, r.from(), r.toExclusive(), provider);
        }
        log.debug("dashboard.monthly dbMs={} rows={} range={}~{} provider={}", (System.nanoTime() - startedAt) / 1_000_000, rows.size(), from, toInclusive, provider);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<MonthlyUsagePoint> monthlySeriesByTeam(String teamId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateMonthlyByTeam(teamId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<MonthlyUsagePoint> monthlySeriesByTeam(
            String teamId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId
    ) {
        if (!restrictTeamToApiKey(apiKeyId)) {
            return monthlySeriesByTeam(teamId, from, toInclusive, provider);
        }
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateMonthlyForTeamFromLogs(teamId, r.from(), r.toExclusive(), provider, apiKeyId.trim());
    }

    @Transactional(readOnly = true)
    public List<MonthlyUsagePoint> monthlySeriesByTeamAndUser(String teamId, String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateMonthlyByTeamAndUser(teamId, userId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<ModelUsageAggregate> byModel(String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        return byModel(userId, from, toInclusive, provider, UsageDataContext.PERSONAL, null, null);
    }

    @Transactional(readOnly = true)
    public List<ModelUsageAggregate> byModel(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return byModel(userId, from, toInclusive, provider, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public List<ModelUsageAggregate> byModel(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Range r = validateRange(from, toInclusive);
        long startedAt = System.nanoTime();
        String key = normalizeApiKey(apiKeyId);
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        List<ModelUsageAggregate> rows;
        if (key != null) {
            rows = analyticsJdbcRepository.aggregateByModelForUserFromLogs(
                    userId, r.from(), r.toExclusive(), provider, key, dataContext, teamScope);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY && teamScope != null) {
            rows = analyticsJdbcRepository.aggregateByModelForTeamAndUser(
                    teamScope, userId, r.from(), r.toExclusive(), provider);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY) {
            rows = analyticsJdbcRepository.aggregateByModelTeamMemberOnly(userId, r.from(), r.toExclusive(), provider);
        } else {
            rows = analyticsJdbcRepository.aggregateByModel(userId, r.from(), r.toExclusive(), provider);
        }
        log.debug("dashboard.byModel dbMs={} rows={} range={}~{} provider={}", (System.nanoTime() - startedAt) / 1_000_000, rows.size(), from, toInclusive, provider);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ModelUsageAggregate> byModelForTeam(String teamId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateByModelForTeam(teamId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<ModelUsageAggregate> byModelForTeam(
            String teamId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId
    ) {
        if (!restrictTeamToApiKey(apiKeyId)) {
            return byModelForTeam(teamId, from, toInclusive, provider);
        }
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateByModelForTeamFromLogs(teamId, r.from(), r.toExclusive(), provider, apiKeyId.trim());
    }

    @Transactional(readOnly = true)
    public TeamUsageSeriesBundle teamUsageSeriesForBff(
            String teamId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId
    ) {
        Range r = validateRange(from, toInclusive);
        long span = ChronoUnit.DAYS.between(from, toInclusive);
        if (span == 0) {
            String keyFilter = restrictTeamToApiKey(apiKeyId) ? apiKeyId.trim() : "";
            List<HourlyUsagePoint> hourly = analyticsJdbcRepository.aggregateHourlyForKstDayForTeam(
                    teamId,
                    r.from(),
                    r.toExclusive(),
                    provider,
                    keyFilter
            );
            List<UsageSeriesPoint> rows = hourly.stream()
                    .map(row -> new UsageSeriesPoint(
                            String.format("%02d:00", row.hour()),
                            row.requestCount(),
                            row.errorCount(),
                            0L,
                            row.estimatedCostUsd()
                    ))
                    .toList();
            return new TeamUsageSeriesBundle(UsageSeriesUnit.HOUR, rows);
        }
        if (span <= 30) {
            List<DailyUsagePoint> daily = dailySeriesByTeam(teamId, from, toInclusive, provider, apiKeyId);
            List<UsageSeriesPoint> rows = daily.stream()
                    .map(row -> new UsageSeriesPoint(
                            row.date().toString(),
                            row.requestCount(),
                            row.errorCount(),
                            row.inputTokens(),
                            row.estimatedCost()
                    ))
                    .toList();
            return new TeamUsageSeriesBundle(UsageSeriesUnit.DAY, rows);
        }
        List<MonthlyUsagePoint> monthly = monthlySeriesByTeam(teamId, from, toInclusive, provider, apiKeyId);
        List<UsageSeriesPoint> rows = monthly.stream()
                .map(row -> new UsageSeriesPoint(
                        row.yearMonth(),
                        row.requestCount(),
                        row.errorCount(),
                        row.inputTokens(),
                        row.estimatedCost()
                ))
                .toList();
        return new TeamUsageSeriesBundle(UsageSeriesUnit.MONTH, rows);
    }

    @Transactional(readOnly = true)
    public List<ModelUsageAggregate> byModelForTeamAndUser(String teamId, String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateByModelForTeamAndUser(teamId, userId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<UsageSeriesPoint> series(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageSeriesUnit unit
    ) {
        return series(userId, from, toInclusive, provider, unit, UsageDataContext.PERSONAL, null, null);
    }

    @Transactional(readOnly = true)
    public List<UsageSeriesPoint> series(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageSeriesUnit unit,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return series(userId, from, toInclusive, provider, unit, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public List<UsageSeriesPoint> series(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageSeriesUnit unit,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Range r = validateRange(from, toInclusive);
        long startedAt = System.nanoTime();
        String key = normalizeApiKey(apiKeyId);
        String keyFilter = key != null ? key : "";
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        if (unit == UsageSeriesUnit.HOUR) {
            long days = ChronoUnit.DAYS.between(from, toInclusive) + 1;
            if (days != 1) {
                throw new IllegalArgumentException("HOUR unit requires a single-day range");
            }
            List<UsageSeriesPoint> rows = analyticsJdbcRepository
                    .aggregateHourlyForKstDayUserScoped(
                            userId, r.from(), r.toExclusive(), provider, dataContext, keyFilter, teamScope)
                    .stream()
                    .map(row -> new UsageSeriesPoint(
                            String.format("%02d:00", row.hour()),
                            row.requestCount(),
                            row.errorCount(),
                            0L,
                            row.estimatedCostUsd()
                    ))
                    .toList();
            log.debug("dashboard.series unit=HOUR dbAndMapMs={} rows={} range={}~{} provider={}", (System.nanoTime() - startedAt) / 1_000_000, rows.size(), from, toInclusive, provider);
            return rows;
        }
        if (unit == UsageSeriesUnit.DAY) {
            List<DailyUsagePoint> dailyPoints = dailySeries(userId, from, toInclusive, provider, dataContext, apiKeyId, teamId);
            List<UsageSeriesPoint> rows = dailyPoints.stream()
                    .map(row -> new UsageSeriesPoint(
                            row.date().toString(),
                            row.requestCount(),
                            row.errorCount(),
                            row.inputTokens(),
                            row.estimatedCost()
                    ))
                    .toList();
            log.debug("dashboard.series unit=DAY dbAndMapMs={} rows={} range={}~{} provider={}", (System.nanoTime() - startedAt) / 1_000_000, rows.size(), from, toInclusive, provider);
            return rows;
        }
        List<MonthlyUsagePoint> monthlyPoints = monthlySeries(userId, from, toInclusive, provider, dataContext, apiKeyId, teamId);
        List<UsageSeriesPoint> rows = monthlyPoints.stream()
                .map(row -> new UsageSeriesPoint(
                        row.yearMonth(),
                        row.requestCount(),
                        row.errorCount(),
                        row.inputTokens(),
                        row.estimatedCost()
                ))
                .toList();
        log.debug("dashboard.series unit=MONTH dbAndMapMs={} rows={} range={}~{} provider={}", (System.nanoTime() - startedAt) / 1_000_000, rows.size(), from, toInclusive, provider);
        return rows;
    }

    /**
     * Latency and success/error rates per bucket from {@code usage_recorded_log} (same scope rules as {@link #series}).
     */
    @Transactional(readOnly = true)
    public List<LatencyStabilityPoint> latencyStabilitySeries(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageSeriesUnit unit,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return latencyStabilitySeries(userId, from, toInclusive, provider, unit, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public List<LatencyStabilityPoint> latencyStabilitySeries(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageSeriesUnit unit,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Range r = validateRange(from, toInclusive);
        String key = normalizeApiKey(apiKeyId);
        String keyFilter = key != null ? key : "";
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        if (unit == UsageSeriesUnit.HOUR) {
            long days = ChronoUnit.DAYS.between(from, toInclusive) + 1;
            if (days != 1) {
                throw new IllegalArgumentException("HOUR unit requires a single-day range");
            }
            return analyticsJdbcRepository.aggregateLatencyStabilityHourlyForKstDayUserScoped(
                    userId,
                    r.from(),
                    r.toExclusive(),
                    provider,
                    dataContext,
                    keyFilter,
                    teamScope
            );
        }
        if (unit == UsageSeriesUnit.DAY) {
            return analyticsJdbcRepository.aggregateLatencyStabilityDailyForUserFromLogs(
                    userId,
                    from,
                    toInclusive,
                    r.from(),
                    r.toExclusive(),
                    provider,
                    keyFilter,
                    dataContext,
                    teamScope
            );
        }
        YearMonth fromYm = YearMonth.from(from);
        YearMonth toYm = YearMonth.from(toInclusive);
        return analyticsJdbcRepository.aggregateLatencyStabilityMonthlyForUserFromLogs(
                userId,
                fromYm,
                toYm,
                r.from(),
                r.toExclusive(),
                provider,
                keyFilter,
                dataContext,
                teamScope
        );
    }

    /**
     * Average latency for the selected range vs the immediately preceding window of equal length (for dashboard banner).
     */
    @Transactional(readOnly = true)
    public LatencyInsightResponse latencyInsight(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return latencyInsight(userId, from, toInclusive, provider, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public LatencyInsightResponse latencyInsight(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Range r = validateRange(from, toInclusive);
        long days = ChronoUnit.DAYS.between(from, toInclusive) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        Instant prevStart = prevFrom.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant prevEndExclusive = prevTo.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        String key = normalizeApiKey(apiKeyId);
        String keyFilter = key != null ? key : "";
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        Double current = analyticsJdbcRepository.aggregateAvgLatencyMsForUserFromLogs(
                userId,
                r.from(),
                r.toExclusive(),
                provider,
                keyFilter,
                dataContext,
                teamScope
        );
        Double previous = analyticsJdbcRepository.aggregateAvgLatencyMsForUserFromLogs(
                userId,
                prevStart,
                prevEndExclusive,
                provider,
                keyFilter,
                dataContext,
                teamScope
        );
        Double changePercent = null;
        if (current != null && previous != null) {
            if (previous > 0) {
                changePercent = (current - previous) / previous * 100.0;
            } else if (current > 0) {
                changePercent = 100.0;
            }
        }
        return new LatencyInsightResponse(current, previous, changePercent);
    }

    /**
     * §2.1 — KST calendar day intraday vs same elapsed window on the previous day.
     */
    @Transactional(readOnly = true)
    public UsageCostIntradayKpiResponse costIntradayKpi(String userId, AiProvider provider) {
        return costIntradayKpi(userId, provider, UsageDataContext.PERSONAL, null, null);
    }

    @Transactional(readOnly = true)
    public UsageCostIntradayKpiResponse costIntradayKpi(
            String userId,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return costIntradayKpi(userId, provider, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public UsageCostIntradayKpiResponse costIntradayKpi(
            String userId,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Instant now = clock.instant();
        LocalDate todayKst = LocalDate.ofInstant(now, DASHBOARD_ZONE);
        Instant dayStart = todayKst.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant dayEndExclusive = todayKst.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant windowEnd = now.isBefore(dayEndExclusive) ? now : dayEndExclusive;

        String key = normalizeApiKey(apiKeyId);
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        BigDecimal todayCost;
        BigDecimal yesterdayCost;
        if (key != null) {
            todayCost = analyticsJdbcRepository.sumEstimatedCostForUserFromLogs(
                    userId, dayStart, windowEnd, provider, key, dataContext, teamScope);
            Duration elapsed = Duration.between(dayStart, windowEnd);
            Instant yStart = dayStart.minus(1, ChronoUnit.DAYS);
            Instant yEnd = yStart.plus(elapsed);
            yesterdayCost = analyticsJdbcRepository.sumEstimatedCostForUserFromLogs(
                    userId, yStart, yEnd, provider, key, dataContext, teamScope);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY && teamScope != null) {
            todayCost = analyticsJdbcRepository.sumEstimatedCostByTeamAndUser(
                    teamScope, userId, dayStart, windowEnd, provider);
            Duration elapsed = Duration.between(dayStart, windowEnd);
            Instant yStart = dayStart.minus(1, ChronoUnit.DAYS);
            Instant yEnd = yStart.plus(elapsed);
            yesterdayCost = analyticsJdbcRepository.sumEstimatedCostByTeamAndUser(
                    teamScope, userId, yStart, yEnd, provider);
        } else if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY) {
            todayCost = analyticsJdbcRepository.sumEstimatedCostTeamMemberOnly(userId, dayStart, windowEnd, provider);
            Duration elapsed = Duration.between(dayStart, windowEnd);
            Instant yStart = dayStart.minus(1, ChronoUnit.DAYS);
            Instant yEnd = yStart.plus(elapsed);
            yesterdayCost = analyticsJdbcRepository.sumEstimatedCostTeamMemberOnly(userId, yStart, yEnd, provider);
        } else {
            todayCost = analyticsJdbcRepository.sumEstimatedCost(userId, dayStart, windowEnd, provider);
            Duration elapsed = Duration.between(dayStart, windowEnd);
            Instant yStart = dayStart.minus(1, ChronoUnit.DAYS);
            Instant yEnd = yStart.plus(elapsed);
            yesterdayCost = analyticsJdbcRepository.sumEstimatedCost(userId, yStart, yEnd, provider);
        }

        BigDecimal rate = null;
        if (yesterdayCost.compareTo(BigDecimal.ZERO) > 0) {
            rate = todayCost
                    .subtract(yesterdayCost)
                    .divide(yesterdayCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new UsageCostIntradayKpiResponse(todayCost, yesterdayCost, rate, windowEnd, todayKst);
    }

    /**
     * §2.2 — Hourly buckets for one KST date (00–23, zero-padded in repository).
     */
    @Transactional(readOnly = true)
    public List<HourlyUsagePoint> hourlySeriesForKstDate(String userId, LocalDate kstDate, AiProvider provider) {
        return hourlySeriesForKstDate(userId, kstDate, provider, UsageDataContext.PERSONAL, null, null);
    }

    @Transactional(readOnly = true)
    public List<HourlyUsagePoint> hourlySeriesForKstDate(
            String userId,
            LocalDate kstDate,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId
    ) {
        return hourlySeriesForKstDate(userId, kstDate, provider, dataContext, apiKeyId, null);
    }

    @Transactional(readOnly = true)
    public List<HourlyUsagePoint> hourlySeriesForKstDate(
            String userId,
            LocalDate kstDate,
            AiProvider provider,
            UsageDataContext dataContext,
            String apiKeyId,
            String teamId
    ) {
        Instant kstDayStart = kstDate.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant kstDayEndExclusive = kstDate.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        String key = normalizeApiKey(apiKeyId);
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        return analyticsJdbcRepository.aggregateHourlyForKstDayUserScoped(
                userId,
                kstDayStart,
                kstDayEndExclusive,
                provider,
                dataContext,
                key != null ? key : "",
                teamScope
        );
    }

    @Transactional(readOnly = true)
    public PagedLogsResponse logs(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId,
            Boolean requestSuccessful,
            String modelMask,
            String reasoningPresence,
            int page,
            int size,
            UsageDataContext dataContext
    ) {
        return logs(
                userId,
                from,
                toInclusive,
                provider,
                apiKeyId,
                requestSuccessful,
                modelMask,
                reasoningPresence,
                page,
                size,
                dataContext,
                null);
    }

    @Transactional(readOnly = true)
    public PagedLogsResponse logs(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId,
            Boolean requestSuccessful,
            String modelMask,
            String reasoningPresence,
            int page,
            int size,
            UsageDataContext dataContext,
            String teamId
    ) {
        long startedAt = System.nanoTime();
        Range r = validateRange(from, toInclusive);
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(200, Math.max(1, size));
        String keyFilter = apiKeyId != null && apiKeyId.isBlank() ? null : apiKeyId;
        String reasoningFilter = normalizeReasoningPresence(reasoningPresence);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
        String teamScope = teamMemberDashboardScope(dataContext, teamId);
        Page<UsageRecordedLogEntity> p =
                dataContext == UsageDataContext.TEAM_MEMBER_ONLY && teamScope != null
                ? logRepository.pageLogsByTeamAndUser(
                        teamScope,
                        userId,
                        r.from(),
                        r.toExclusive(),
                        provider,
                        keyFilter,
                        requestSuccessful,
                        modelMask,
                        reasoningFilter,
                        pageable
                )
                : dataContext == UsageDataContext.TEAM_MEMBER_ONLY
                ? logRepository.pageLogsTeamMember(
                        userId,
                        r.from(),
                        r.toExclusive(),
                        provider,
                        keyFilter,
                        requestSuccessful,
                        modelMask,
                        reasoningFilter,
                        pageable
                )
                : logRepository.pageLogsPersonal(
                        userId,
                        r.from(),
                        r.toExclusive(),
                        provider,
                        keyFilter,
                        requestSuccessful,
                        modelMask,
                        reasoningFilter,
                        pageable
                );
        List<UsageLogEntryResponse> content = p.getContent().stream().map(this::toLogDto).toList();
        log.debug("dashboard.logs totalMs={} page={} size={} rows={} range={}~{} provider={}",
                (System.nanoTime() - startedAt) / 1_000_000,
                pageIndex,
                pageSize,
                content.size(),
                from,
                toInclusive,
                provider
        );
        return new PagedLogsResponse(
                content,
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PagedLogsResponse logsByTeam(
            String teamId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId,
            Boolean requestSuccessful,
            String modelMask,
            String reasoningPresence,
            int page,
            int size
    ) {
        Range r = validateRange(from, toInclusive);
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(200, Math.max(1, size));
        String keyFilter = apiKeyId != null && apiKeyId.isBlank() ? null : apiKeyId;
        String reasoningFilter = normalizeReasoningPresence(reasoningPresence);
        Page<UsageRecordedLogEntity> p = logRepository.pageLogsByTeam(
                teamId,
                r.from(),
                r.toExclusive(),
                provider,
                keyFilter,
                requestSuccessful,
                modelMask,
                reasoningFilter,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"))
        );
        List<UsageLogEntryResponse> content = p.getContent().stream().map(this::toLogDto).toList();
        return new PagedLogsResponse(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PagedLogsResponse logsByTeamAndUser(
            String teamId,
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String apiKeyId,
            Boolean requestSuccessful,
            String modelMask,
            String reasoningPresence,
            int page,
            int size
    ) {
        Range r = validateRange(from, toInclusive);
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(200, Math.max(1, size));
        String keyFilter = apiKeyId != null && apiKeyId.isBlank() ? null : apiKeyId;
        String reasoningFilter = normalizeReasoningPresence(reasoningPresence);
        Page<UsageRecordedLogEntity> p = logRepository.pageLogsByTeamAndUser(
                teamId,
                userId,
                r.from(),
                r.toExclusive(),
                provider,
                keyFilter,
                requestSuccessful,
                modelMask,
                reasoningFilter,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"))
        );
        List<UsageLogEntryResponse> content = p.getContent().stream().map(this::toLogDto).toList();
        return new PagedLogsResponse(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<UsageLogApiKeyItemResponse> logApiKeys(String userId, AiProvider provider) {
        return logApiKeys(userId, null, provider, UsageDataContext.PERSONAL);
    }

    @Transactional(readOnly = true)
    public List<UsageLogApiKeyItemResponse> logApiKeys(String userId, AiProvider provider, UsageDataContext dataContext) {
        return logApiKeys(userId, null, provider, dataContext);
    }

    /**
     * Personal-scope alias list: metadata rows owned by {@code userId}, merged with rows owned by
     * {@code alternatePersonalSubjectUserId} when non-blank and distinct (e.g. gateway {@code X-User-Id} is email while
     * identity events keyed metadata by platform subject id).
     */
    @Transactional(readOnly = true)
    public List<UsageLogApiKeyItemResponse> logApiKeys(
            String userId,
            String alternatePersonalSubjectUserId,
            AiProvider provider,
            UsageDataContext dataContext
    ) {
        if (dataContext == UsageDataContext.TEAM_MEMBER_ONLY) {
            Instant to = clock.instant();
            Instant from = to.minus(LOG_API_KEY_LOOKUP_DAYS, ChronoUnit.DAYS);
            return logRepository.findDistinctApiKeysForUserTeamMemberInRange(userId, from, to, provider);
        }
        String providerStr = provider == null ? null : provider.name();
        String providerLower = provider == null ? null : provider.name().toLowerCase(Locale.ROOT);
        Map<String, UsageLogApiKeyItemResponse> byApiKeyId = new LinkedHashMap<>();
        for (ApiKeyMetadataEntity m : personalApiKeyMetadataRowsForAliasList(
                userId,
                alternatePersonalSubjectUserId,
                providerLower
        )) {
            byApiKeyId.put(m.getKeyId(), new UsageLogApiKeyItemResponse(m.getKeyId(), m.getAlias(), m.getStatus()));
        }
        Instant logTo = clock.instant();
        Instant logFrom = logTo.minus(LOG_API_KEY_LOOKUP_DAYS, ChronoUnit.DAYS);
        appendPersonalDistinctLogKeys(byApiKeyId, userId.trim(), logFrom, logTo, provider);
        if (StringUtils.hasText(alternatePersonalSubjectUserId)) {
            String alt = alternatePersonalSubjectUserId.trim();
            if (!alt.equals(userId.trim())) {
                appendPersonalDistinctLogKeys(byApiKeyId, alt, logFrom, logTo, provider);
            }
        }
        List<UsageLogApiKeyItemResponse> out = List.copyOf(byApiKeyId.values());
        if (log.isInfoEnabled()) {
            log.info(
                    "Personal dashboard API key alias list loaded userId={} keyCount={} providerFilter={}",
                    maskUserIdForLog(userId),
                    out.size(),
                    providerStr != null ? providerStr : "ALL"
            );
        }
        return out;
    }

    private List<ApiKeyMetadataEntity> personalApiKeyMetadataRowsForAliasList(
            String primaryUserId,
            String alternatePersonalSubjectUserId,
            String providerLower
    ) {
        String primary = primaryUserId.trim();
        Map<String, ApiKeyMetadataEntity> byKeyId = new LinkedHashMap<>();
        appendPersonalMetadataForAliasList(byKeyId, primary, providerLower);
        if (StringUtils.hasText(alternatePersonalSubjectUserId)) {
            String alt = alternatePersonalSubjectUserId.trim();
            if (!alt.equals(primary)) {
                appendPersonalMetadataForAliasList(byKeyId, alt, providerLower);
            }
        }
        return byKeyId.values().stream()
                .sorted(Comparator.comparing(ApiKeyMetadataEntity::getUpdatedAt).reversed())
                .toList();
    }

    private void appendPersonalMetadataForAliasList(
            Map<String, ApiKeyMetadataEntity> byKeyId,
            String userId,
            String providerLower
    ) {
        for (ApiKeyMetadataEntity m : apiKeyMetadataRepository.findPersonalKeysForDashboard(userId, providerLower)) {
            byKeyId.merge(
                    m.getKeyId(),
                    m,
                    (a, b) -> a.getUpdatedAt().isAfter(b.getUpdatedAt()) ? a : b
            );
        }
    }

    /**
     * Adds keys seen under the user's personal log scope (same window as team-member key pickers)
     * when metadata ownership does not match the gateway subject yet.
     */
    private void appendPersonalDistinctLogKeys(
            Map<String, UsageLogApiKeyItemResponse> byApiKeyId,
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        for (UsageLogApiKeyItemResponse row : logRepository.findDistinctApiKeysForUserPersonalInRange(
                userId,
                from,
                toExclusive,
                provider
        )) {
            byApiKeyId.putIfAbsent(row.apiKeyId(), row);
        }
    }

    private static String maskUserIdForLog(String userId) {
        if (userId == null || userId.isBlank()) {
            return "-";
        }
        String t = userId.trim();
        int keep = Math.min(4, t.length());
        return t.substring(0, keep) + "***";
    }

    private UsageLogEntryResponse toLogDto(UsageRecordedLogEntity e) {
        JsonNode details = parseProviderTokenDetails(e.getProviderTokenDetails());
        return new UsageLogEntryResponse(
                e.getEventId(),
                e.getOccurredAt(),
                e.getCorrelationId(),
                e.getProvider().name(),
                e.getModel(),
                null,
                resolveDisplayAlias(e),
                e.getApiKeyMetadata() != null && e.getApiKeyMetadata().getStatus() != null
                        ? e.getApiKeyMetadata().getStatus().name()
                        : null,
                e.getPromptTokens(),
                e.getCompletionTokens(),
                resolveEstimatedReasoningTokens(e),
                getLongOrNull(details, "prompt_cached_tokens"),
                getLongOrNull(details, "prompt_audio_tokens"),
                getLongOrNull(details, "completion_reasoning_tokens"),
                getLongOrNull(details, "completion_audio_tokens"),
                getLongOrNull(details, "completion_accepted_prediction_tokens"),
                getLongOrNull(details, "completion_rejected_prediction_tokens"),
                e.getTotalTokens(),
                e.getEstimatedCost(),
                e.getRequestPath(),
                e.getUpstreamHost(),
                e.getStreaming(),
                e.isRequestSuccessful(),
                e.getUpstreamStatusCode()
        );
    }

    private static String resolveDisplayAlias(UsageRecordedLogEntity e) {
        if (e.getApiKeyMetadata() == null) {
            return null;
        }
        String alias = e.getApiKeyMetadata().getAlias();
        ApiKeyStatus status = e.getApiKeyMetadata().getStatus();
        if (alias == null || alias.isBlank()) {
            return null;
        }
        if (status == ApiKeyStatus.DELETED) {
            return alias + DELETED_ALIAS_SUFFIX;
        }
        return alias;
    }

    private static Long resolveEstimatedReasoningTokens(UsageRecordedLogEntity e) {
        return e.getEstimatedReasoningTokens();
    }

    private JsonNode parseProviderTokenDetails(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long getLongOrNull(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || !node.get(fieldName).canConvertToLong()) {
            return null;
        }
        return node.get(fieldName).longValue();
    }

    private Range validateRange(LocalDate from, LocalDate toInclusive) {
        if (from == null || toInclusive == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (toInclusive.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        long days = ChronoUnit.DAYS.between(from, toInclusive) + 1;
        int max = Math.min(properties.getAnalytics().getMaxRangeDays(), MAX_DASHBOARD_RANGE_DAYS);
        if (days > max) {
            throw new IllegalArgumentException("Date range too large (max " + max + " days)");
        }
        Instant start = from.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant toExclusive = toInclusive.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        return new Range(start, toExclusive);
    }

    private record Range(Instant from, Instant toExclusive) {
    }

    /**
     * Logs query: optional filter by whether estimated reasoning tokens are present ({@code > 0}).
     * Allowed: {@code present}, {@code absent}; anything else is treated as no filter.
     */
    private static String normalizeReasoningPresence(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        if ("present".equals(v) || "absent".equals(v)) {
            return v;
        }
        return null;
    }

    private static boolean restrictTeamToApiKey(String apiKeyId) {
        return apiKeyId != null && !apiKeyId.isBlank();
    }

    private static String normalizeApiKey(String apiKeyId) {
        if (apiKeyId == null || apiKeyId.isBlank()) {
            return null;
        }
        return apiKeyId.trim();
    }

    public record TeamUsageSeriesBundle(UsageSeriesUnit unit, List<UsageSeriesPoint> points) {
    }
}
