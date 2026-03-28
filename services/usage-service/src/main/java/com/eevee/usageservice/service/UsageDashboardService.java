package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.PagedLogsResponse;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class UsageDashboardService {

    private final UsageAnalyticsJdbcRepository analyticsJdbcRepository;
    private final UsageRecordedLogRepository logRepository;
    private final UsageServiceProperties properties;

    public UsageDashboardService(
            UsageAnalyticsJdbcRepository analyticsJdbcRepository,
            UsageRecordedLogRepository logRepository,
            UsageServiceProperties properties
    ) {
        this.analyticsJdbcRepository = analyticsJdbcRepository;
        this.logRepository = logRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summary(String userId, LocalDate from, LocalDate toInclusive) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateSummary(userId, r.from(), r.toExclusive());
    }

    @Transactional(readOnly = true)
    public List<DailyUsagePoint> dailySeries(String userId, LocalDate from, LocalDate toInclusive) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateDaily(userId, r.from(), r.toExclusive());
    }

    @Transactional(readOnly = true)
    public List<MonthlyUsagePoint> monthlySeries(String userId, LocalDate from, LocalDate toInclusive) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateMonthly(userId, r.from(), r.toExclusive());
    }

    @Transactional(readOnly = true)
    public List<ModelUsageAggregate> byModel(String userId, LocalDate from, LocalDate toInclusive) {
        Range r = validateRange(from, toInclusive);
        return analyticsJdbcRepository.aggregateByModel(userId, r.from(), r.toExclusive());
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
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = toInclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Range(start, toExclusive);
    }

    private record Range(Instant from, Instant toExclusive) {
    }
}