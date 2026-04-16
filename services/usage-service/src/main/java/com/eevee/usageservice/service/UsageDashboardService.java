package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.HourlyUsagePoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.PagedLogsResponse;
import com.eevee.usageservice.api.dto.UsageCostIntradayKpiResponse;
import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.api.dto.UsageLogEntryResponse;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

@Service
public class UsageDashboardService {

    /** Dashboard date ranges and buckets align with Korea Standard Time (same as identity-service convention). */
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");
    private static final String DELETED_ALIAS_SUFFIX = " (삭제)";

    private static final int LOG_API_KEY_LOOKUP_DAYS = 30;

    private final UsageAnalyticsJdbcRepository analyticsJdbcRepository;
    private final UsageRecordedLogRepository logRepository;
    private final UsageServiceProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public UsageDashboardService(
            UsageAnalyticsJdbcRepository analyticsJdbcRepository,
            UsageRecordedLogRepository logRepository,
            UsageServiceProperties properties,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.analyticsJdbcRepository = analyticsJdbcRepository;
        this.logRepository = logRepository;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
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
     * §2.1 — KST calendar day intraday vs same elapsed window on the previous day.
     */
    @Transactional(readOnly = true)
    public UsageCostIntradayKpiResponse costIntradayKpi(String userId, AiProvider provider) {
        Instant now = clock.instant();
        LocalDate todayKst = LocalDate.ofInstant(now, DASHBOARD_ZONE);
        Instant dayStart = todayKst.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant dayEndExclusive = todayKst.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant windowEnd = now.isBefore(dayEndExclusive) ? now : dayEndExclusive;

        BigDecimal todayCost = analyticsJdbcRepository.sumEstimatedCost(userId, dayStart, windowEnd, provider);

        Duration elapsed = Duration.between(dayStart, windowEnd);
        Instant yStart = dayStart.minus(1, ChronoUnit.DAYS);
        Instant yEnd = yStart.plus(elapsed);
        BigDecimal yesterdayCost = analyticsJdbcRepository.sumEstimatedCost(userId, yStart, yEnd, provider);

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
        Instant kstDayStart = kstDate.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant kstDayEndExclusive = kstDate.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        return analyticsJdbcRepository.aggregateHourlyForKstDay(userId, kstDayStart, kstDayEndExclusive, provider);
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
            int page,
            int size
    ) {
        Range r = validateRange(from, toInclusive);
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(200, Math.max(1, size));
        String keyFilter = apiKeyId != null && apiKeyId.isBlank() ? null : apiKeyId;
        Page<UsageRecordedLogEntity> p = logRepository.pageLogs(
                userId,
                r.from(),
                r.toExclusive(),
                provider,
                keyFilter,
                requestSuccessful,
                modelMask,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"))
        );
        List<UsageLogEntryResponse> content = p.getContent().stream().map(this::toLogDto).toList();
        return new PagedLogsResponse(
                content,
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<UsageLogApiKeyItemResponse> logApiKeys(String userId, AiProvider provider) {
        Instant to = clock.instant();
        Instant from = to.minus(LOG_API_KEY_LOOKUP_DAYS, ChronoUnit.DAYS);
        return logRepository.findDistinctApiKeysForUserInRange(userId, from, to, provider);
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
        if (e.getEstimatedReasoningTokens() != null) {
            return e.getEstimatedReasoningTokens();
        }
        if (e.getTotalTokens() == null || e.getPromptTokens() == null || e.getCompletionTokens() == null) {
            return null;
        }
        long fallback = e.getTotalTokens() - e.getPromptTokens() - e.getCompletionTokens();
        return Math.max(fallback, 0L);
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
