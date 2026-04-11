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
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class UsageAnalyticsJdbcRepository {

    private static final String ERR_PRED = "(NOT request_successful OR (upstream_status_code IS NOT NULL AND upstream_status_code >= 400))";

    /** Must match {@code UsageDashboardService} date-range zone (KST). */
    private static final String BUCKET_ZONE = "Asia/Seoul";

    private final JdbcTemplate jdbc;

    public UsageAnalyticsJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String providerName(AiProvider provider) {
        return provider == null ? null : provider.name();
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
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                AND (? IS NULL OR provider = ?)
                """.formatted(ERR_PRED);
        String p = providerName(provider);
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
                p,
                p
        );
    }

    /**
     * Sum of {@code estimated_cost} only (for intraday KPI windows).
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
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                AND (? IS NULL OR provider = ?)
                """;
        String p = providerName(provider);
        BigDecimal v = jdbc.queryForObject(
                sql,
                (rs, rowNum) -> rs.getBigDecimal(1),
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p,
                p
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
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                AND (? IS NULL OR provider = ?)
                GROUP BY (occurred_at AT TIME ZONE '%s')::date
                ORDER BY d
                """.formatted(BUCKET_ZONE, ERR_PRED, BUCKET_ZONE);
        String p = providerName(provider);
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
                p,
                p
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
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                AND (? IS NULL OR provider = ?)
                GROUP BY to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM')
                ORDER BY ym
                """.formatted(BUCKET_ZONE, ERR_PRED, BUCKET_ZONE);
        String p = providerName(provider);
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
                p,
                p
        );
    }

    public List<ModelUsageAggregate> aggregateByModel(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT COALESCE(NULLIF(trim(model), ''), '_unknown') AS m,
                       provider,
                       COUNT(*)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                AND (? IS NULL OR provider = ?)
                GROUP BY COALESCE(NULLIF(trim(model), ''), '_unknown'), provider
                ORDER BY COUNT(*) DESC
                """;
        String p = providerName(provider);
        return jdbc.query(
                sql,
                (rs, rowNum) -> new ModelUsageAggregate(
                        rs.getString("m"),
                        rs.getString("provider"),
                        rs.getLong(3),
                        rs.getLong(4)
                ),
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p,
                p
        );
    }

    public List<HourlyUsagePoint> aggregateHourlyForKstDay(
            String userId,
            LocalDate kstDay,
            AiProvider provider
    ) {
        String sql = """
                SELECT (EXTRACT(HOUR FROM (occurred_at AT TIME ZONE '%s')))::int AS h,
                       COUNT(*)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND ((occurred_at AT TIME ZONE '%s')::date = ?::date)
                  AND (? IS NULL OR provider = ?)
                GROUP BY h
                ORDER BY h
                """.formatted(BUCKET_ZONE, BUCKET_ZONE);
        String p = providerName(provider);
        return jdbc.query(
                sql,
                (rs, rowNum) -> new HourlyUsagePoint(
                        rs.getInt("h"),
                        rs.getLong(2),
                        rs.getBigDecimal(3) != null ? rs.getBigDecimal(3) : BigDecimal.ZERO
                ),
                userId,
                Date.valueOf(kstDay),
                p,
                p
        );
    }
}
