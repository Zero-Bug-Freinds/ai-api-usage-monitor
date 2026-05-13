package com.eevee.billingservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public class TeamApiKeyAggregationJdbc {

    private final JdbcTemplate jdbcTemplate;

    public TeamApiKeyAggregationJdbc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertMonthly(LocalDate monthStartDate, long teamApiKeyId, BigDecimal costDelta) {
        jdbcTemplate.update(
                """
                        INSERT INTO team_api_key_monthly_expenditure_agg
                            (month_start_date, team_api_key_id, total_cost_usd, is_finalized, finalized_at)
                        VALUES (?, ?, ?, false, NULL)
                        ON CONFLICT (month_start_date, team_api_key_id)
                        DO UPDATE SET
                            total_cost_usd = CASE
                                WHEN team_api_key_monthly_expenditure_agg.is_finalized THEN team_api_key_monthly_expenditure_agg.total_cost_usd
                                ELSE team_api_key_monthly_expenditure_agg.total_cost_usd + EXCLUDED.total_cost_usd
                            END
                        """,
                monthStartDate,
                teamApiKeyId,
                costDelta
        );
    }

    public void upsertDaily(LocalDate aggDate, long teamApiKeyId, BigDecimal costDelta) {
        jdbcTemplate.update(
                """
                        INSERT INTO team_api_key_daily_expenditure_agg
                            (agg_date, team_api_key_id, total_cost_usd)
                        VALUES (?, ?, ?)
                        ON CONFLICT (agg_date, team_api_key_id)
                        DO UPDATE SET
                            total_cost_usd = team_api_key_daily_expenditure_agg.total_cost_usd + EXCLUDED.total_cost_usd
                        """,
                aggDate,
                teamApiKeyId,
                costDelta
        );
    }

    public BigDecimal sumMonthlyCostUsdForTeam(LocalDate monthStartDate, long teamId) {
        BigDecimal v = jdbcTemplate.queryForObject(
                """
                        SELECT coalesce(sum(agg.total_cost_usd), 0)
                        FROM team_api_key_monthly_expenditure_agg agg
                        JOIN billing_team_api_key k
                          ON k.team_api_key_id = agg.team_api_key_id
                        WHERE agg.month_start_date = ?
                          AND k.team_id = ?
                        """,
                BigDecimal.class,
                monthStartDate,
                teamId
        );
        return v != null ? v : BigDecimal.ZERO;
    }

    public BigDecimal sumDailyCostUsdForTeam(LocalDate from, LocalDate to, long teamId) {
        BigDecimal v = jdbcTemplate.queryForObject(
                """
                        SELECT coalesce(sum(d.total_cost_usd), 0)
                        FROM team_api_key_daily_expenditure_agg d
                        JOIN billing_team_api_key k
                          ON k.team_api_key_id = d.team_api_key_id
                        WHERE d.agg_date >= ?
                          AND d.agg_date <= ?
                          AND k.team_id = ?
                        """,
                BigDecimal.class,
                from,
                to,
                teamId
        );
        return v != null ? v : BigDecimal.ZERO;
    }

    public BigDecimal sumMonthlyBudgetUsdForTeam(long teamId) {
        BigDecimal v = jdbcTemplate.queryForObject(
                """
                        SELECT coalesce(sum(monthly_budget_usd), 0)
                        FROM billing_team_api_key
                        WHERE team_id = ?
                          AND status <> 'DELETED'
                        """,
                BigDecimal.class,
                teamId
        );
        return v != null ? v : BigDecimal.ZERO;
    }

    public BigDecimal sumMonthlyCostUsdForTeamApiKey(LocalDate monthStartDate, long teamApiKeyId) {
        // When a key exists in the read model but no usage was recorded yet for the month,
        // this table can legitimately have no row. Treat missing row as 0 spend.
        BigDecimal v = jdbcTemplate.query(
                """
                        SELECT total_cost_usd
                        FROM team_api_key_monthly_expenditure_agg
                        WHERE month_start_date = ?
                          AND team_api_key_id = ?
                        """,
                ps -> {
                    ps.setObject(1, monthStartDate);
                    ps.setLong(2, teamApiKeyId);
                },
                rs -> rs.next() ? rs.getBigDecimal(1) : null
        );
        return v != null ? v : BigDecimal.ZERO;
    }

    public BigDecimal sumDailyCostUsdForTeamApiKey(LocalDate from, LocalDate to, long teamApiKeyId) {
        BigDecimal v = jdbcTemplate.query(
                """
                        SELECT coalesce(sum(total_cost_usd), 0)
                        FROM team_api_key_daily_expenditure_agg
                        WHERE agg_date >= ?
                          AND agg_date <= ?
                          AND team_api_key_id = ?
                        """,
                ps -> {
                    ps.setObject(1, from);
                    ps.setObject(2, to);
                    ps.setLong(3, teamApiKeyId);
                },
                rs -> rs.next() ? rs.getBigDecimal(1) : null
        );
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Removes all aggregate rows for a team API key after physical delete.
     *
     * @return deleted row counts {@code [team_api_key_daily_expenditure_agg, team_api_key_monthly_expenditure_agg]}
     */
    public int[] deleteAggregatesForTeamApiKey(long teamApiKeyId) {
        int daily = jdbcTemplate.update(
                """
                        DELETE FROM team_api_key_daily_expenditure_agg
                        WHERE team_api_key_id = ?
                        """,
                teamApiKeyId
        );
        int monthly = jdbcTemplate.update(
                """
                        DELETE FROM team_api_key_monthly_expenditure_agg
                        WHERE team_api_key_id = ?
                        """,
                teamApiKeyId
        );
        return new int[] {daily, monthly};
    }
}

