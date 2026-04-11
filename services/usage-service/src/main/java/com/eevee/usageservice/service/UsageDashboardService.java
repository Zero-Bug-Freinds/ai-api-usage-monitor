package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.HourlyUsagePoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.PagedLogsResponse;
import com.eevee.usageservice.api.dto.UsageCostIntradayKpiResponse;
import com.eevee.usageservice.api.dto.UsageLogEntryResponse;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsageDashboardService {

    /** Dashboard date ranges and buckets align with Korea Standard Time (same as identity-service convention). */
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");

    private final UsageAnalyticsJdbcRepository analyticsJdbcRepository;
    private final UsageRecordedLogRepository logRepository;
    private final UsageServiceProperties properties;
    private final Clock clock;

    public UsageDashboardService(
            UsageAnalyticsJdbcRepository analyticsJdbcRepository,
            UsageRecordedLogRepository logRepository,
            UsageServiceProperties properties,
            Clock clock
    ) {
        this.analyticsJdbcRepository = analyticsJdbcRepository;
        this.logRepository = logRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summary(String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateSummary(userId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<DailyUsagePoint> dailySeries(String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateDaily(userId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<MonthlyUsagePoint> monthlySeries(String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateMonthly(userId, r.from(), r.toExclusive(), provider);
    }

    @Transactional(readOnly = true)
    public List<ModelUsageAggregate> byModel(String userId, LocalDate from, LocalDate toInclusive, AiProvider provider) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateByModel(userId, r.from(), r.toExclusive(), provider);
    }

    /**
     * Today cumulative cost vs yesterday same elapsed window (KST). {@code changeRatePercent} is null if yesterday sum is 0.
     */
    @Transactional(readOnly = true)
    public UsageCostIntradayKpiResponse costIntradayKpi(String userId, AiProvider provider) {
        Clock kstClock = clock.withZone(DASHBOARD_ZONE);
        LocalDate kstToday = LocalDate.now(kstClock);
        Instant now = clock.instant();
        Instant dayStart = kstToday.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant dayEnd = kstToday.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant windowEnd = now.isBefore(dayEnd) ? now : dayEnd;
        if (windowEnd.isBefore(dayStart)) {
            windowEnd = dayStart;
        }

        BigDecimal todayCost = analyticsJdbcRepository.sumEstimatedCost(userId, dayStart, windowEnd, provider);

        Duration elapsed = Duration.between(dayStart, windowEnd);
        Instant yStart = dayStart.minus(1, ChronoUnit.DAYS);
        Instant yEnd = yStart.plus(elapsed);
        BigDecimal yesterdayCost = analyticsJdbcRepository.sumEstimatedCost(userId, yStart, yEnd, provider);

        BigDecimal changeRate = null;
        if (yesterdayCost.compareTo(BigDecimal.ZERO) > 0) {
            changeRate = todayCost.subtract(yesterdayCost)
                    .divide(yesterdayCost, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new UsageCostIntradayKpiResponse(kstToday, windowEnd, todayCost, yesterdayCost, changeRate);
    }

    /**
     * 24 rows (hours 0–23) for the given KST calendar day; missing hours are zero-filled.
     */
    @Transactional(readOnly = true)
    public List<HourlyUsagePoint> hourlySeries(String userId, LocalDate kstDay, AiProvider provider) {
        validateRange(kstDay, kstDay);
        List<HourlyUsagePoint> rows = analyticsJdbcRepository.aggregateHourlyForKstDay(userId, kstDay, provider);
        Map<Integer, HourlyUsagePoint> byHour = new HashMap<>();
        for (HourlyUsagePoint row : rows) {
            byHour.put(row.hour(), row);
        }
        List<HourlyUsagePoint> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            out.add(byHour.getOrDefault(h, new HourlyUsagePoint(h, 0L, BigDecimal.ZERO)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PagedLogsResponse logs(
            String userId,
            LocalDate from,
            LocalDate toInclusive,
            AiProvider provider,
            String modelMask,
            int page,
            int size
    ) {
        Range r = validateRange(from, toInclusive);
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(200, Math.max(1, size));
        Page<UsageRecordedLogEntity> p = logRepository.pageLogs(
                userId,
                r.from(),
                r.toExclusive(),
                provider,
                modelMask,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"))
        );
        List<UsageLogEntryResponse> content = p.getContent().stream().map(UsageDashboardService::toLogDto).toList();
        return new PagedLogsResponse(
                content,
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages()
        );
    }

    private static UsageLogEntryResponse toLogDto(UsageRecordedLogEntity e) {
        return new UsageLogEntryResponse(
                e.getEventId(),
                e.getOccurredAt(),
                e.getCorrelationId(),
                e.getProvider().name(),
                e.getModel(),
                e.getPromptTokens(),
                e.getCompletionTokens(),
                e.getTotalTokens(),
                e.getEstimatedCost(),
                e.getRequestPath(),
                e.getUpstreamHost(),
                e.getStreaming(),
                e.isRequestSuccessful(),
                e.getUpstreamStatusCode()
        );
    }

    private Range validateRange(LocalDate from, LocalDate toInclusive) {
        if (from == null || toInclusive == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (toInclusive.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        long days = ChronoUnit.DAYS.between(from, toInclusive) + 1;
        int max = properties.getAnalytics().getMaxRangeDays();
        if (days > max) {
            throw new IllegalArgumentException("Date range too large (max " + max + " days)");
        }
        Instant start = from.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant toExclusive = toInclusive.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        return new Range(start, toExclusive);
    }

    private record Range(Instant from, Instant toExclusive) {
    }
}
