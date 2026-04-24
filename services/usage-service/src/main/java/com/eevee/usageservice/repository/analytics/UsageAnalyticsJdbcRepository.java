package com.eevee.usageservice.repository.analytics;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.HourlyUsagePoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class UsageAnalyticsJdbcRepository {

    private static final String ERR_PRED = "(NOT request_successful OR (upstream_status_code IS NOT NULL AND upstream_status_code >= 400))";

    /**
     * PostgreSQL: avoid "could not determine data type of parameter" when only {@code IS NULL} binds are used.
     */
    private static final String PROVIDER_FILTER = " AND ((?::text) IS NULL OR provider::text = ?::text)";

    /** Must match {@code UsageDashboardService} date-range zone (KST). */
    private static final String BUCKET_ZONE = "Asia/Seoul";

    private final JdbcTemplate jdbc;

    public UsageAnalyticsJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UsageSummaryResponse aggregateSummary(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?%s
                """.formatted(ERR_PRED, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new UsageSummaryResponse(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getBigDecimal(4) != null ? rs.getBigDecimal(4) : BigDecimal.ZERO
                ),
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    /**
     * Sum of {@code estimated_cost} over {@code [from, toExclusive)} with optional provider filter.
     */
    public BigDecimal sumEstimatedCost(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?%s
                """.formatted(PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        BigDecimal v = jdbc.queryForObject(
                sql,
                BigDecimal.class,
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
        return v != null ? v : BigDecimal.ZERO;
    }

    public List<DailyUsagePoint> aggregateDaily(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT (occurred_at AT TIME ZONE '%s')::date AS d,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?%s
                GROUP BY (occurred_at AT TIME ZONE '%s')::date
                ORDER BY d
                """.formatted(BUCKET_ZONE, ERR_PRED, PROVIDER_FILTER, BUCKET_ZONE);
        String p1 = provider == null ? null : provider.name();
        return jdbc.query(
                sql,
                (rs, rowNum) -> {
                    LocalDate day = rs.getDate("d").toLocalDate();
                    return new DailyUsagePoint(
                            day,
                            rs.getLong(2),
                            rs.getLong(3),
                            rs.getLong(4),
                            rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : BigDecimal.ZERO
                    );
                },
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    public List<MonthlyUsagePoint> aggregateMonthly(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM') AS ym,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?%s
                GROUP BY to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM')
                ORDER BY ym
                """.formatted(BUCKET_ZONE, ERR_PRED, PROVIDER_FILTER, BUCKET_ZONE);
        String p1 = provider == null ? null : provider.name();
        return jdbc.query(
                sql,
                (rs, rowNum) -> new MonthlyUsagePoint(
                        rs.getString("ym"),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : BigDecimal.ZERO
                ),
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    public List<ModelUsageAggregate> aggregateByModel(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT COALESCE(NULLIF(trim(model), ''), lower(provider::text) || '_unknown') AS m,
                       provider,
                       COUNT(*)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_reasoning_tokens), 0)::bigint,
                       COALESCE(SUM(completion_tokens), 0)::bigint
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?%s
                GROUP BY COALESCE(NULLIF(trim(model), ''), lower(provider::text) || '_unknown'), provider
                ORDER BY COUNT(*) DESC
                """.formatted(PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.query(
                sql,
                (rs, rowNum) -> new ModelUsageAggregate(
                        rs.getString("m"),
                        rs.getString("provider"),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getLong(5),
                        rs.getLong(6)
                ),
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    /**
     * Hour buckets 0–23 for one KST calendar day (§2.2). Missing hours are filled in the service layer.
     */
    public List<HourlyUsagePoint> aggregateHourlyForKstDay(
            String userId,
            Instant kstDayStartUtc,
            Instant kstDayEndExclusiveUtc,
            AiProvider provider
    ) {
        String sql = """
                SELECT (EXTRACT(HOUR FROM (occurred_at AT TIME ZONE '%s')))::int AS h,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        List<HourlyUsagePoint> rows = jdbc.query(
                sql,
                (rs, rowNum) -> new HourlyUsagePoint(
                        rs.getInt("h"),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getBigDecimal(4) != null ? rs.getBigDecimal(4) : BigDecimal.ZERO
                ),
                userId,
                Timestamp.from(kstDayStartUtc),
                Timestamp.from(kstDayEndExclusiveUtc),
                p1,
                p1
        );
        Map<Integer, HourlyUsagePoint> byHour = new HashMap<>();
        for (HourlyUsagePoint row : rows) {
            byHour.put(row.hour(), row);
        }
        List<HourlyUsagePoint> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            HourlyUsagePoint existing = byHour.get(h);
            if (existing != null) {
                out.add(existing);
            } else {
                out.add(new HourlyUsagePoint(h, 0L, 0L, BigDecimal.ZERO));
            }
        }
        return out;
    }
}
