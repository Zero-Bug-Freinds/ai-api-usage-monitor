package com.eevee.usageservice.repository.analytics;

import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import com.eevee.usageservice.config.UsageServiceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Repository
public class UsageAnalyticsJdbcRepository {

    private static final String ERR_PRED = "(NOT request_successful OR (upstream_status_code IS NOT NULL AND upstream_status_code >= 400))";

    private final JdbcTemplate jdbc;
    private final String reportingZoneId;

    public UsageAnalyticsJdbcRepository(JdbcTemplate jdbc, UsageServiceProperties usageServiceProperties) {
        this.jdbc = jdbc;
        this.reportingZoneId = ZoneId.of(usageServiceProperties.getReporting().getTimeZone()).getId();
    }

    public UsageSummaryResponse aggregateSummary(String userId, Instant from, Instant toExclusive) {
        String sql = """
                SELECT COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                """.formatted(ERR_PRED);
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
                Timestamp.from(toExclusive)
        );
    }

    public List<DailyUsagePoint> aggregateDaily(String userId, Instant from, Instant toExclusive) {
        String sql = """
                SELECT (occurred_at AT TIME ZONE '%s')::date AS d,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                GROUP BY (occurred_at AT TIME ZONE '%s')::date
                ORDER BY d
                """.formatted(reportingZoneId, ERR_PRED, reportingZoneId);
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
                Timestamp.from(toExclusive)
        );
    }

    public List<MonthlyUsagePoint> aggregateMonthly(String userId, Instant from, Instant toExclusive) {
        String sql = """
                SELECT to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM') AS ym,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                GROUP BY to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM')
                ORDER BY ym
                """.formatted(reportingZoneId, ERR_PRED, reportingZoneId);
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
                Timestamp.from(toExclusive)
        );
    }

    public List<ModelUsageAggregate> aggregateByModel(String userId, Instant from, Instant toExclusive) {
        String sql = """
                SELECT COALESCE(NULLIF(trim(model), ''), '_unknown') AS m,
                       provider,
                       COUNT(*)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint
                FROM usage_recorded_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                GROUP BY COALESCE(NULLIF(trim(model), ''), '_unknown'), provider
                ORDER BY COUNT(*) DESC
                """;
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
                Timestamp.from(toExclusive)
        );
    }
}