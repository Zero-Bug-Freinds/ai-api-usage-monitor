package com.eevee.usageservice.repository.analytics;

import com.eevee.usageservice.api.dto.HourlyUsagePoint;
import com.eevee.usageservice.api.dto.UsageDataContext;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: {@code TEAM_MEMBER_ONLY} + non-blank {@code teamId} appends {@code AND team_id = ?}
 * after the occurred_at range in SQL; JDBC arguments must be
 * {@code userId, from, toExclusive, teamId, ...} (never team before timestamps).
 * <p>
 * If latency-only endpoints still fail after this, check DB migration
 * {@code V6__usage_recorded_log_latency_ms.sql} ({@code latency_ms} column).
 */
@SuppressWarnings("unchecked")
class UsageAnalyticsJdbcRepositoryTeamScopeBindOrderTest {

    @Test
    void aggregateHourlyForKstDayUserScoped_singleTeam_bindsUserThenRangeThenTeamId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UsageAnalyticsJdbcRepository repo = new UsageAnalyticsJdbcRepository(jdbc);
        Instant kstDayStart = Instant.parse("2026-05-11T00:00:00Z");
        Instant kstDayEndExclusive = Instant.parse("2026-05-12T00:00:00Z");
        when(jdbc.query(
                anyString(),
                any(RowMapper.class),
                eq("user-1"),
                eq(Timestamp.from(kstDayStart)),
                eq(Timestamp.from(kstDayEndExclusive)),
                eq("team-9"),
                eq(""),
                eq(""),
                isNull(),
                isNull()
        )).thenReturn(List.<HourlyUsagePoint>of());

        repo.aggregateHourlyForKstDayUserScoped(
                "user-1",
                kstDayStart,
                kstDayEndExclusive,
                null,
                UsageDataContext.TEAM_MEMBER_ONLY,
                "",
                "team-9"
        );

        verify(jdbc).query(
                anyString(),
                any(RowMapper.class),
                eq("user-1"),
                eq(Timestamp.from(kstDayStart)),
                eq(Timestamp.from(kstDayEndExclusive)),
                eq("team-9"),
                eq(""),
                eq(""),
                isNull(),
                isNull()
        );
    }

    @Test
    void aggregateSummaryForUserFromLogs_singleTeam_bindsUserThenRangeThenTeamId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UsageAnalyticsJdbcRepository repo = new UsageAnalyticsJdbcRepository(jdbc);
        Instant from = LocalDate.of(2026, 5, 11).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = LocalDate.of(2026, 5, 12).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(jdbc.queryForObject(
                anyString(),
                any(RowMapper.class),
                eq("user-1"),
                eq(Timestamp.from(from)),
                eq(Timestamp.from(toExclusive)),
                eq("team-9"),
                eq(""),
                eq(""),
                isNull(),
                isNull()
        )).thenReturn(new UsageSummaryResponse(0L, 0L, 0L, BigDecimal.ZERO));

        repo.aggregateSummaryForUserFromLogs(
                "user-1",
                from,
                toExclusive,
                null,
                "",
                UsageDataContext.TEAM_MEMBER_ONLY,
                "team-9"
        );

        verify(jdbc).queryForObject(
                anyString(),
                any(RowMapper.class),
                eq("user-1"),
                eq(Timestamp.from(from)),
                eq(Timestamp.from(toExclusive)),
                eq("team-9"),
                eq(""),
                eq(""),
                isNull(),
                isNull()
        );
    }
}
