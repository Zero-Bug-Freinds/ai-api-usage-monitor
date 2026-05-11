package com.eevee.usageservice.repository.analytics;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.HourlyUsagePoint;
import com.eevee.usageservice.api.dto.LatencyStabilityPoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.UsageDataContext;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import com.eevee.usageservice.api.dto.UsageTeamUserSlice;
import com.eevee.usageservice.api.dto.ProviderModelCostTokenRow;
import com.eevee.usageservice.api.dto.UsageWindowTotals;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private static final String PERSONAL_SCOPE_FILTER_SUMMARY = " AND team_id = ''";
    private static final String PERSONAL_SCOPE_FILTER_LOGS = " AND COALESCE(team_id, '') = ''";
    private static final String TEAM_MEMBER_ONLY_SCOPE_FILTER_SUMMARY = " AND team_id <> ''";
    private static final String TEAM_MEMBER_ONLY_SCOPE_FILTER_LOGS = " AND COALESCE(team_id, '') <> ''";

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
                SELECT COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                """.formatted(PERSONAL_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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

    public UsageSummaryResponse aggregateSummaryByTeam(
            String teamId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                """.formatted(BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new UsageSummaryResponse(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getBigDecimal(4) != null ? rs.getBigDecimal(4) : BigDecimal.ZERO
                ),
                teamId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    public UsageSummaryResponse aggregateSummaryByTeamAndUser(
            String teamId,
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND user_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                """.formatted(BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new UsageSummaryResponse(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getBigDecimal(4) != null ? rs.getBigDecimal(4) : BigDecimal.ZERO
                ),
                teamId,
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    public UsageSummaryResponse aggregateSummaryTeamMemberOnly(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                """.formatted(TEAM_MEMBER_ONLY_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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
                SELECT COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                """.formatted(PERSONAL_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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

    public BigDecimal sumEstimatedCostTeamMemberOnly(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                """.formatted(TEAM_MEMBER_ONLY_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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
                SELECT usage_date AS d,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY usage_date
                ORDER BY d
                """.formatted(PERSONAL_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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

    public List<DailyUsagePoint> aggregateDailyTeamMemberOnly(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT usage_date AS d,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY usage_date
                ORDER BY d
                """.formatted(TEAM_MEMBER_ONLY_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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

    public List<DailyUsagePoint> aggregateDailyByTeam(
            String teamId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT usage_date AS d,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY usage_date
                ORDER BY d
                """.formatted(BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.query(
                sql,
                (rs, rowNum) -> new DailyUsagePoint(
                        rs.getDate("d").toLocalDate(),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : BigDecimal.ZERO
                ),
                teamId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    public List<DailyUsagePoint> aggregateDailyByTeamAndUser(
            String teamId,
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT usage_date AS d,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND user_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY usage_date
                ORDER BY d
                """.formatted(BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.query(
                sql,
                (rs, rowNum) -> new DailyUsagePoint(
                        rs.getDate("d").toLocalDate(),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : BigDecimal.ZERO
                ),
                teamId,
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
                SELECT to_char(usage_date, 'YYYY-MM') AS ym,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY to_char(usage_date, 'YYYY-MM')
                ORDER BY ym
                """.formatted(PERSONAL_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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

    public List<MonthlyUsagePoint> aggregateMonthlyTeamMemberOnly(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT to_char(usage_date, 'YYYY-MM') AS ym,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY to_char(usage_date, 'YYYY-MM')
                ORDER BY ym
                """.formatted(TEAM_MEMBER_ONLY_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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

    public List<MonthlyUsagePoint> aggregateMonthlyByTeam(
            String teamId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT to_char(usage_date, 'YYYY-MM') AS ym,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY to_char(usage_date, 'YYYY-MM')
                ORDER BY ym
                """.formatted(BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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
                teamId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    public List<MonthlyUsagePoint> aggregateMonthlyByTeamAndUser(
            String teamId,
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT to_char(usage_date, 'YYYY-MM') AS ym,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(error_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(total_cost), 0)
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND user_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY to_char(usage_date, 'YYYY-MM')
                ORDER BY ym
                """.formatted(BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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
                teamId,
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
                SELECT model AS m,
                       provider,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(reasoning_tokens), 0)::bigint,
                       COALESCE(SUM(completion_tokens), 0)::bigint
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY model, provider
                ORDER BY SUM(request_count) DESC
                """.formatted(PERSONAL_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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

    public List<ModelUsageAggregate> aggregateByModelTeamMemberOnly(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT model AS m,
                       provider,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(reasoning_tokens), 0)::bigint,
                       COALESCE(SUM(completion_tokens), 0)::bigint
                FROM daily_usage_summary
                WHERE user_id = ?
                %s
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY model, provider
                ORDER BY SUM(request_count) DESC
                """.formatted(TEAM_MEMBER_ONLY_SCOPE_FILTER_SUMMARY, BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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

    public List<ModelUsageAggregate> aggregateByModelForTeam(
            String teamId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT model AS m,
                       provider,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(reasoning_tokens), 0)::bigint,
                       COALESCE(SUM(completion_tokens), 0)::bigint
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY model, provider
                ORDER BY SUM(request_count) DESC
                """.formatted(BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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
                teamId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    public List<ModelUsageAggregate> aggregateByModelForTeamAndUser(
            String teamId,
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider
    ) {
        String sql = """
                SELECT model AS m,
                       provider,
                       COALESCE(SUM(request_count), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(reasoning_tokens), 0)::bigint,
                       COALESCE(SUM(completion_tokens), 0)::bigint
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND user_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)%s
                GROUP BY model, provider
                ORDER BY SUM(request_count) DESC
                """.formatted(BUCKET_ZONE, BUCKET_ZONE, PROVIDER_FILTER);
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
                teamId,
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                p1,
                p1
        );
    }

    private static String userScopeLogsFragment(UsageDataContext scope) {
        return scope == UsageDataContext.TEAM_MEMBER_ONLY ? TEAM_MEMBER_ONLY_SCOPE_FILTER_LOGS : PERSONAL_SCOPE_FILTER_LOGS;
    }

    public UsageSummaryResponse aggregateSummaryForUserFromLogs(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter,
            UsageDataContext scope
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sql = """
                SELECT COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                """.formatted(ERR_PRED, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
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
                af,
                af,
                p1,
                p1
        );
    }

    public List<DailyUsagePoint> aggregateDailyForUserFromLogs(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter,
            UsageDataContext scope
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sql = """
                SELECT ((occurred_at AT TIME ZONE '%s'))::date AS d,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.query(
                sql,
                (rs, rowNum) -> new DailyUsagePoint(
                        rs.getDate("d").toLocalDate(),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : BigDecimal.ZERO
                ),
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                af,
                af,
                p1,
                p1
        );
    }

    public List<MonthlyUsagePoint> aggregateMonthlyForUserFromLogs(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter,
            UsageDataContext scope
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sql = """
                SELECT to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM') AS ym,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
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
                af,
                af,
                p1,
                p1
        );
    }

    public List<ModelUsageAggregate> aggregateByModelForUserFromLogs(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter,
            UsageDataContext scope
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sql = """
                SELECT COALESCE(NULLIF(TRIM(model), ''), LOWER(provider::text) || '_unknown') AS m,
                       provider::text,
                       COUNT(*)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_reasoning_tokens), 0)::bigint,
                       COALESCE(SUM(completion_tokens), 0)::bigint
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                GROUP BY model, provider
                ORDER BY COUNT(*) DESC
                """.formatted(scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
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
                af,
                af,
                p1,
                p1
        );
    }

    public BigDecimal sumEstimatedCostForUserFromLogs(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter,
            UsageDataContext scope
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sql = """
                SELECT COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                """.formatted(scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        BigDecimal v = jdbc.queryForObject(
                sql,
                BigDecimal.class,
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                af,
                af,
                p1,
                p1
        );
        return v != null ? v : BigDecimal.ZERO;
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
        return aggregateHourlyForKstDayUserScoped(
                userId,
                kstDayStartUtc,
                kstDayEndExclusiveUtc,
                provider,
                UsageDataContext.PERSONAL,
                ""
        );
    }

    public List<HourlyUsagePoint> aggregateHourlyForKstDayUserScoped(
            String userId,
            Instant kstDayStartUtc,
            Instant kstDayEndExclusiveUtc,
            AiProvider provider,
            UsageDataContext scope,
            String apiKeyFilter
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sql = """
                SELECT (EXTRACT(HOUR FROM (occurred_at AT TIME ZONE '%s')))::int AS h,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
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
                af,
                af,
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

    private static final String TEAM_API_KEY_FILTER = " AND ((?::text) = '' OR api_key_id = (?::text))";

    /**
     * Hour buckets for one KST day, team scope (logs). {@code apiKeyFilter} empty string = all keys for team.
     */
    public List<HourlyUsagePoint> aggregateHourlyForKstDayForTeam(
            String teamId,
            Instant kstDayStartUtc,
            Instant kstDayEndExclusiveUtc,
            AiProvider provider,
            String apiKeyFilter
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String sql = """
                SELECT (EXTRACT(HOUR FROM (occurred_at AT TIME ZONE '%s')))::int AS h,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE team_id = ?
                  AND occurred_at >= ? AND occurred_at < ?%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        List<HourlyUsagePoint> rows = jdbc.query(
                sql,
                (rs, rowNum) -> new HourlyUsagePoint(
                        rs.getInt("h"),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getBigDecimal(4) != null ? rs.getBigDecimal(4) : BigDecimal.ZERO
                ),
                teamId,
                Timestamp.from(kstDayStartUtc),
                Timestamp.from(kstDayEndExclusiveUtc),
                af,
                af,
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

    public UsageSummaryResponse aggregateSummaryForTeamFromLogs(
            String teamId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String sql = """
                SELECT COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE team_id = ?
                  AND occurred_at >= ? AND occurred_at < ?%s%s
                """.formatted(ERR_PRED, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new UsageSummaryResponse(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getBigDecimal(4) != null ? rs.getBigDecimal(4) : BigDecimal.ZERO
                ),
                teamId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                af,
                af,
                p1,
                p1
        );
    }

    public List<DailyUsagePoint> aggregateDailyForTeamFromLogs(
            String teamId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String sql = """
                SELECT ((occurred_at AT TIME ZONE '%s'))::date AS d,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE team_id = ?
                  AND occurred_at >= ? AND occurred_at < ?%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        return jdbc.query(
                sql,
                (rs, rowNum) -> new DailyUsagePoint(
                        rs.getDate("d").toLocalDate(),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : BigDecimal.ZERO
                ),
                teamId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                af,
                af,
                p1,
                p1
        );
    }

    public List<MonthlyUsagePoint> aggregateMonthlyForTeamFromLogs(
            String teamId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String sql = """
                SELECT to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM') AS ym,
                       COUNT(*)::bigint,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_cost), 0)
                FROM usage_recorded_log
                WHERE team_id = ?
                  AND occurred_at >= ? AND occurred_at < ?%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
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
                teamId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                af,
                af,
                p1,
                p1
        );
    }

    public List<ModelUsageAggregate> aggregateByModelForTeamFromLogs(
            String teamId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String sql = """
                SELECT COALESCE(NULLIF(TRIM(model), ''), LOWER(provider::text) || '_unknown') AS m,
                       provider::text,
                       COUNT(*)::bigint,
                       COALESCE(SUM(prompt_tokens), 0)::bigint,
                       COALESCE(SUM(estimated_reasoning_tokens), 0)::bigint,
                       COALESCE(SUM(completion_tokens), 0)::bigint
                FROM usage_recorded_log
                WHERE team_id = ?
                  AND occurred_at >= ? AND occurred_at < ?%s%s
                GROUP BY model, provider
                ORDER BY COUNT(*) DESC
                """.formatted(TEAM_API_KEY_FILTER, PROVIDER_FILTER);
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
                teamId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                af,
                af,
                p1,
                p1
        );
    }

    public List<UsageTeamUserSlice> findDistinctTeamUserSlices(LocalDate minUsageDateInclusive) {
        String sql = """
                SELECT DISTINCT team_id, user_id
                FROM daily_usage_summary
                WHERE usage_date >= ?
                ORDER BY team_id, user_id
                """;
        return jdbc.query(
                sql,
                (rs, rowNum) -> new UsageTeamUserSlice(
                        rs.getString("team_id") != null ? rs.getString("team_id") : "",
                        rs.getString("user_id")
                ),
                minUsageDateInclusive
        );
    }

    public UsageWindowTotals sumCostAndTokensByTeamAndUser(
            String teamId,
            String userId,
            Instant from,
            Instant toExclusive
    ) {
        String sql = """
                SELECT COALESCE(SUM(total_cost), 0),
                       COALESCE(SUM(total_tokens), 0)::bigint
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND user_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)
                """.formatted(BUCKET_ZONE, BUCKET_ZONE);
        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new UsageWindowTotals(
                        rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : BigDecimal.ZERO,
                        rs.getLong(2)
                ),
                teamId,
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive)
        );
    }

    public List<ProviderModelCostTokenRow> aggregateProviderModelCostAndTokensByTeamAndUser(
            String teamId,
            String userId,
            Instant from,
            Instant toExclusive
    ) {
        String sql = """
                SELECT provider,
                       model,
                       COALESCE(SUM(total_cost), 0),
                       COALESCE(SUM(total_tokens), 0)::bigint
                FROM daily_usage_summary
                WHERE team_id = ?
                  AND user_id = ?
                  AND usage_date >= ((? AT TIME ZONE '%s')::date)
                  AND usage_date < ((? AT TIME ZONE '%s')::date)
                GROUP BY provider, model
                ORDER BY COALESCE(SUM(total_cost), 0) DESC
                """.formatted(BUCKET_ZONE, BUCKET_ZONE);
        return jdbc.query(
                sql,
                (rs, rowNum) -> new ProviderModelCostTokenRow(
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getBigDecimal(3) != null ? rs.getBigDecimal(3) : BigDecimal.ZERO,
                        rs.getLong(4)
                ),
                teamId,
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive)
        );
    }

    /**
     * Average latency (ms) over rows with non-null {@code latency_ms}, same filters as user-scoped log aggregates.
     */
    public Double aggregateAvgLatencyMsForUserFromLogs(
            String userId,
            Instant from,
            Instant toExclusive,
            AiProvider provider,
            String apiKeyFilter,
            UsageDataContext scope
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sql = """
                SELECT AVG(latency_ms)::double precision
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  AND latency_ms IS NOT NULL
                  %s%s%s
                """.formatted(scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        Double v = jdbc.query(
                sql,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return rs.getObject(1) != null ? rs.getDouble(1) : null;
                },
                userId,
                Timestamp.from(from),
                Timestamp.from(toExclusive),
                af,
                af,
                p1,
                p1
        );
        return v;
    }

    /**
     * Hourly latency stability for one KST day; fills hours 0–23 (missing hours use zeros / null latencies).
     */
    public List<LatencyStabilityPoint> aggregateLatencyStabilityHourlyForKstDayUserScoped(
            String userId,
            Instant kstDayStartUtc,
            Instant kstDayEndExclusiveUtc,
            AiProvider provider,
            UsageDataContext scope,
            String apiKeyFilter
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sqlStats = """
                SELECT (EXTRACT(HOUR FROM (occurred_at AT TIME ZONE '%s')))::int AS h,
                       COUNT(*)::bigint AS req_cnt,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint AS err_cnt,
                       COALESCE(SUM(COALESCE(total_tokens, 0)), 0)::bigint AS tok_sum,
                       (AVG(latency_ms) FILTER (WHERE latency_ms IS NOT NULL))::double precision AS avg_ms,
                       MIN(latency_ms) FILTER (WHERE latency_ms IS NOT NULL) AS min_ms,
                       MAX(latency_ms) FILTER (WHERE latency_ms IS NOT NULL) AS max_ms,
                       (PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms))::double precision AS p95,
                       (PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms))::double precision AS p99,
                       CASE WHEN SUM(COALESCE(total_tokens, 0)) > 0
                            THEN (COALESCE(SUM(latency_ms) FILTER (WHERE latency_ms IS NOT NULL), 0))::double precision
                                 / SUM(COALESCE(total_tokens, 0))::double precision
                            ELSE NULL END AS ms_per_tok
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        Map<Integer, LatencyStabilityPoint> byHour = new HashMap<>();
        jdbc.query(
                sqlStats,
                rs -> {
                    while (rs.next()) {
                        int h = rs.getInt("h");
                        long req = rs.getLong("req_cnt");
                        long err = rs.getLong("err_cnt");
                        long tok = rs.getLong("tok_sum");
                        double successRate = req > 0 ? (100.0 * (req - err)) / req : 0.0;
                        double errorRate = req > 0 ? (100.0 * err) / req : 0.0;
                        Double avgMs = rs.getObject("avg_ms") != null ? rs.getDouble("avg_ms") : null;
                        Long minMs = rs.getObject("min_ms") != null ? rs.getLong("min_ms") : null;
                        Long maxMs = rs.getObject("max_ms") != null ? rs.getLong("max_ms") : null;
                        Double p95 = rs.getObject("p95") != null ? rs.getDouble("p95") : null;
                        Double p99 = rs.getObject("p99") != null ? rs.getDouble("p99") : null;
                        Double msPerTok = rs.getObject("ms_per_tok") != null ? rs.getDouble("ms_per_tok") : null;
                        String label = String.format("%02d:00", h);
                        byHour.put(
                                h,
                                new LatencyStabilityPoint(
                                        label,
                                        req,
                                        successRate,
                                        errorRate,
                                        tok,
                                        avgMs,
                                        minMs,
                                        maxMs,
                                        p95,
                                        p99,
                                        msPerTok,
                                        null,
                                        null
                                )
                        );
                    }
                    return null;
                },
                userId,
                Timestamp.from(kstDayStartUtc),
                Timestamp.from(kstDayEndExclusiveUtc),
                af,
                af,
                p1,
                p1
        );

        String sqlTop = """
                WITH cnt AS (
                    SELECT (EXTRACT(HOUR FROM (occurred_at AT TIME ZONE '%s')))::int AS h,
                           COALESCE(NULLIF(TRIM(model), ''), LOWER(provider::text) || '_unknown') AS m,
                           provider::text AS p,
                           COUNT(*)::bigint AS c
                    FROM usage_recorded_log
                    WHERE user_id = ?
                      AND occurred_at >= ? AND occurred_at < ?
                      %s%s%s
                    GROUP BY 1, 2, 3
                )
                SELECT DISTINCT ON (h) h, m, p
                FROM cnt
                ORDER BY h, c DESC
                """.formatted(BUCKET_ZONE, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        Map<Integer, String[]> topByHour = new HashMap<>();
        jdbc.query(
                sqlTop,
                rs -> {
                    while (rs.next()) {
                        topByHour.put(rs.getInt("h"), new String[] {rs.getString("m"), rs.getString("p")});
                    }
                    return null;
                },
                userId,
                Timestamp.from(kstDayStartUtc),
                Timestamp.from(kstDayEndExclusiveUtc),
                af,
                af,
                p1,
                p1
        );

        List<LatencyStabilityPoint> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            LatencyStabilityPoint base = byHour.get(h);
            String[] top = topByHour.get(h);
            String label = String.format("%02d:00", h);
            if (base == null) {
                out.add(new LatencyStabilityPoint(
                        label,
                        0L,
                        0.0,
                        0.0,
                        0L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            } else {
                out.add(new LatencyStabilityPoint(
                        label,
                        base.requestCount(),
                        base.successRate(),
                        base.errorRate(),
                        base.totalTokens(),
                        base.avgLatencyMs(),
                        base.minLatencyMs(),
                        base.maxLatencyMs(),
                        base.p95LatencyMs(),
                        base.p99LatencyMs(),
                        base.latencyPerTokenMs(),
                        top != null ? top[0] : null,
                        top != null ? top[1] : null
                ));
            }
        }
        return out;
    }

    public List<LatencyStabilityPoint> aggregateLatencyStabilityDailyForUserFromLogs(
            String userId,
            LocalDate fromInclusive,
            LocalDate toInclusive,
            Instant fromUtc,
            Instant toExclusiveUtc,
            AiProvider provider,
            String apiKeyFilter,
            UsageDataContext scope
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sqlStats = """
                SELECT ((occurred_at AT TIME ZONE '%s'))::date AS d,
                       COUNT(*)::bigint AS req_cnt,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint AS err_cnt,
                       COALESCE(SUM(COALESCE(total_tokens, 0)), 0)::bigint AS tok_sum,
                       (AVG(latency_ms) FILTER (WHERE latency_ms IS NOT NULL))::double precision AS avg_ms,
                       MIN(latency_ms) FILTER (WHERE latency_ms IS NOT NULL) AS min_ms,
                       MAX(latency_ms) FILTER (WHERE latency_ms IS NOT NULL) AS max_ms,
                       (PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms))::double precision AS p95,
                       (PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms))::double precision AS p99,
                       CASE WHEN SUM(COALESCE(total_tokens, 0)) > 0
                            THEN (COALESCE(SUM(latency_ms) FILTER (WHERE latency_ms IS NOT NULL), 0))::double precision
                                 / SUM(COALESCE(total_tokens, 0))::double precision
                            ELSE NULL END AS ms_per_tok
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        Map<LocalDate, LatencyStabilityPoint> byDay = new HashMap<>();
        jdbc.query(
                sqlStats,
                rs -> {
                    while (rs.next()) {
                        LocalDate d = rs.getDate("d").toLocalDate();
                        long req = rs.getLong("req_cnt");
                        long err = rs.getLong("err_cnt");
                        long tok = rs.getLong("tok_sum");
                        double successRate = req > 0 ? (100.0 * (req - err)) / req : 0.0;
                        double errorRate = req > 0 ? (100.0 * err) / req : 0.0;
                        Double avgMs = rs.getObject("avg_ms") != null ? rs.getDouble("avg_ms") : null;
                        Long minMs = rs.getObject("min_ms") != null ? rs.getLong("min_ms") : null;
                        Long maxMs = rs.getObject("max_ms") != null ? rs.getLong("max_ms") : null;
                        Double p95 = rs.getObject("p95") != null ? rs.getDouble("p95") : null;
                        Double p99 = rs.getObject("p99") != null ? rs.getDouble("p99") : null;
                        Double msPerTok = rs.getObject("ms_per_tok") != null ? rs.getDouble("ms_per_tok") : null;
                        String label = d.toString();
                        byDay.put(
                                d,
                                new LatencyStabilityPoint(
                                        label,
                                        req,
                                        successRate,
                                        errorRate,
                                        tok,
                                        avgMs,
                                        minMs,
                                        maxMs,
                                        p95,
                                        p99,
                                        msPerTok,
                                        null,
                                        null
                                )
                        );
                    }
                    return null;
                },
                userId,
                Timestamp.from(fromUtc),
                Timestamp.from(toExclusiveUtc),
                af,
                af,
                p1,
                p1
        );

        String sqlTop = """
                WITH cnt AS (
                    SELECT ((occurred_at AT TIME ZONE '%s'))::date AS d,
                           COALESCE(NULLIF(TRIM(model), ''), LOWER(provider::text) || '_unknown') AS m,
                           provider::text AS p,
                           COUNT(*)::bigint AS c
                    FROM usage_recorded_log
                    WHERE user_id = ?
                      AND occurred_at >= ? AND occurred_at < ?
                      %s%s%s
                    GROUP BY 1, 2, 3
                )
                SELECT DISTINCT ON (d) d, m, p
                FROM cnt
                ORDER BY d, c DESC
                """.formatted(BUCKET_ZONE, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        Map<LocalDate, String[]> topByDay = new HashMap<>();
        jdbc.query(
                sqlTop,
                rs -> {
                    while (rs.next()) {
                        LocalDate d = rs.getDate("d").toLocalDate();
                        topByDay.put(d, new String[] {rs.getString("m"), rs.getString("p")});
                    }
                    return null;
                },
                userId,
                Timestamp.from(fromUtc),
                Timestamp.from(toExclusiveUtc),
                af,
                af,
                p1,
                p1
        );

        List<LatencyStabilityPoint> out = new ArrayList<>();
        for (LocalDate d = fromInclusive; !d.isAfter(toInclusive); d = d.plusDays(1)) {
            LatencyStabilityPoint base = byDay.get(d);
            String[] top = topByDay.get(d);
            String label = d.toString();
            if (base == null) {
                out.add(new LatencyStabilityPoint(
                        label,
                        0L,
                        0.0,
                        0.0,
                        0L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            } else {
                out.add(new LatencyStabilityPoint(
                        label,
                        base.requestCount(),
                        base.successRate(),
                        base.errorRate(),
                        base.totalTokens(),
                        base.avgLatencyMs(),
                        base.minLatencyMs(),
                        base.maxLatencyMs(),
                        base.p95LatencyMs(),
                        base.p99LatencyMs(),
                        base.latencyPerTokenMs(),
                        top != null ? top[0] : null,
                        top != null ? top[1] : null
                ));
            }
        }
        return out;
    }

    public List<LatencyStabilityPoint> aggregateLatencyStabilityMonthlyForUserFromLogs(
            String userId,
            YearMonth fromYm,
            YearMonth toYmInclusive,
            Instant fromUtc,
            Instant toExclusiveUtc,
            AiProvider provider,
            String apiKeyFilter,
            UsageDataContext scope
    ) {
        String af = apiKeyFilter == null ? "" : apiKeyFilter.trim();
        String scopeFrag = userScopeLogsFragment(scope);
        String sqlStats = """
                SELECT to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM') AS ym,
                       COUNT(*)::bigint AS req_cnt,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0)::bigint AS err_cnt,
                       COALESCE(SUM(COALESCE(total_tokens, 0)), 0)::bigint AS tok_sum,
                       (AVG(latency_ms) FILTER (WHERE latency_ms IS NOT NULL))::double precision AS avg_ms,
                       MIN(latency_ms) FILTER (WHERE latency_ms IS NOT NULL) AS min_ms,
                       MAX(latency_ms) FILTER (WHERE latency_ms IS NOT NULL) AS max_ms,
                       (PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms))::double precision AS p95,
                       (PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms))::double precision AS p99,
                       CASE WHEN SUM(COALESCE(total_tokens, 0)) > 0
                            THEN (COALESCE(SUM(latency_ms) FILTER (WHERE latency_ms IS NOT NULL), 0))::double precision
                                 / SUM(COALESCE(total_tokens, 0))::double precision
                            ELSE NULL END AS ms_per_tok
                FROM usage_recorded_log
                WHERE user_id = ?
                  AND occurred_at >= ? AND occurred_at < ?
                  %s%s%s
                GROUP BY 1
                ORDER BY 1
                """.formatted(BUCKET_ZONE, ERR_PRED, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        String p1 = provider == null ? null : provider.name();
        Map<String, LatencyStabilityPoint> byYm = new HashMap<>();
        jdbc.query(
                sqlStats,
                rs -> {
                    while (rs.next()) {
                        String ym = rs.getString("ym");
                        long req = rs.getLong("req_cnt");
                        long err = rs.getLong("err_cnt");
                        long tok = rs.getLong("tok_sum");
                        double successRate = req > 0 ? (100.0 * (req - err)) / req : 0.0;
                        double errorRate = req > 0 ? (100.0 * err) / req : 0.0;
                        Double avgMs = rs.getObject("avg_ms") != null ? rs.getDouble("avg_ms") : null;
                        Long minMs = rs.getObject("min_ms") != null ? rs.getLong("min_ms") : null;
                        Long maxMs = rs.getObject("max_ms") != null ? rs.getLong("max_ms") : null;
                        Double p95 = rs.getObject("p95") != null ? rs.getDouble("p95") : null;
                        Double p99 = rs.getObject("p99") != null ? rs.getDouble("p99") : null;
                        Double msPerTok = rs.getObject("ms_per_tok") != null ? rs.getDouble("ms_per_tok") : null;
                        byYm.put(
                                ym,
                                new LatencyStabilityPoint(
                                        ym,
                                        req,
                                        successRate,
                                        errorRate,
                                        tok,
                                        avgMs,
                                        minMs,
                                        maxMs,
                                        p95,
                                        p99,
                                        msPerTok,
                                        null,
                                        null
                                )
                        );
                    }
                    return null;
                },
                userId,
                Timestamp.from(fromUtc),
                Timestamp.from(toExclusiveUtc),
                af,
                af,
                p1,
                p1
        );

        String sqlTop = """
                WITH cnt AS (
                    SELECT to_char((occurred_at AT TIME ZONE '%s'), 'YYYY-MM') AS ym,
                           COALESCE(NULLIF(TRIM(model), ''), LOWER(provider::text) || '_unknown') AS m,
                           provider::text AS p,
                           COUNT(*)::bigint AS c
                    FROM usage_recorded_log
                    WHERE user_id = ?
                      AND occurred_at >= ? AND occurred_at < ?
                      %s%s%s
                    GROUP BY 1, 2, 3
                )
                SELECT DISTINCT ON (ym) ym, m, p
                FROM cnt
                ORDER BY ym, c DESC
                """.formatted(BUCKET_ZONE, scopeFrag, TEAM_API_KEY_FILTER, PROVIDER_FILTER);
        Map<String, String[]> topByYm = new HashMap<>();
        jdbc.query(
                sqlTop,
                rs -> {
                    while (rs.next()) {
                        topByYm.put(rs.getString("ym"), new String[] {rs.getString("m"), rs.getString("p")});
                    }
                    return null;
                },
                userId,
                Timestamp.from(fromUtc),
                Timestamp.from(toExclusiveUtc),
                af,
                af,
                p1,
                p1
        );

        List<LatencyStabilityPoint> out = new ArrayList<>();
        for (YearMonth ym = fromYm; !ym.isAfter(toYmInclusive); ym = ym.plusMonths(1)) {
            String label = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
            LatencyStabilityPoint base = byYm.get(label);
            String[] top = topByYm.get(label);
            if (base == null) {
                out.add(new LatencyStabilityPoint(
                        label,
                        0L,
                        0.0,
                        0.0,
                        0L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            } else {
                out.add(new LatencyStabilityPoint(
                        label,
                        base.requestCount(),
                        base.successRate(),
                        base.errorRate(),
                        base.totalTokens(),
                        base.avgLatencyMs(),
                        base.minLatencyMs(),
                        base.maxLatencyMs(),
                        base.p95LatencyMs(),
                        base.p99LatencyMs(),
                        base.latencyPerTokenMs(),
                        top != null ? top[0] : null,
                        top != null ? top[1] : null
                ));
            }
        }
        return out;
    }
}
