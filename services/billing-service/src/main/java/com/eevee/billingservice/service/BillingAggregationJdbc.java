package com.eevee.billingservice.service;

import com.eevee.usage.events.AiProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * PostgreSQL upserts for concurrent-safe aggregation without lost updates.
 */
@Repository
public class BillingAggregationJdbc {

    private final JdbcTemplate jdbcTemplate;

    public BillingAggregationJdbc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertDaily(
            LocalDate aggDate,
            String userId,
            String apiKeyId,
            AiProvider provider,
            String model,
            BigDecimal costDelta,
            long promptDelta,
            long completionDelta
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO daily_expenditure_agg
                            (agg_date, user_id, api_key_id, provider, model, total_cost_usd, total_prompt_tokens, total_completion_tokens)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (agg_date, user_id, api_key_id, provider, model)
                        DO UPDATE SET
                            total_cost_usd = daily_expenditure_agg.total_cost_usd + EXCLUDED.total_cost_usd,
                            total_prompt_tokens = daily_expenditure_agg.total_prompt_tokens + EXCLUDED.total_prompt_tokens,
                            total_completion_tokens = daily_expenditure_agg.total_completion_tokens + EXCLUDED.total_completion_tokens
                        """,
                aggDate,
                userId,
                apiKeyId,
                provider.name(),
                model,
                costDelta,
                promptDelta,
                completionDelta
        );
    }

    public void upsertMonthly(
            LocalDate monthStartDate,
            String userId,
            String apiKeyId,
            BigDecimal costDelta
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO monthly_expenditure_agg
                            (month_start_date, user_id, api_key_id, total_cost_usd, is_finalized, finalized_at)
                        VALUES (?, ?, ?, ?, false, NULL)
                        ON CONFLICT (month_start_date, user_id, api_key_id)
                        DO UPDATE SET
                            total_cost_usd = CASE
                                WHEN monthly_expenditure_agg.is_finalized THEN monthly_expenditure_agg.total_cost_usd
                                ELSE monthly_expenditure_agg.total_cost_usd + EXCLUDED.total_cost_usd
                            END
                        """,
                monthStartDate,
                userId,
                apiKeyId,
                costDelta
        );
    }

    public BigDecimal findMonthlyTotalUsd(LocalDate monthStartDate, String userId, String apiKeyId) {
        return jdbcTemplate.query(
                """
                        SELECT total_cost_usd
                        FROM monthly_expenditure_agg
                        WHERE month_start_date = ?
                          AND user_id = ?
                          AND api_key_id = ?
                        """,
                ps -> {
                    ps.setObject(1, monthStartDate);
                    ps.setString(2, userId);
                    ps.setString(3, apiKeyId);
                },
                rs -> rs.next() ? rs.getBigDecimal(1) : null
        );
    }

    public void upsertSeen(String userId, String apiKeyId, AiProvider provider, Instant occurredAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO billing_user_api_key_seen
                            (user_id, api_key_id, provider, first_seen_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (user_id, api_key_id, provider)
                        DO UPDATE SET
                            first_seen_at = LEAST(billing_user_api_key_seen.first_seen_at, EXCLUDED.first_seen_at)
                        """,
                userId,
                apiKeyId,
                provider.name(),
                java.sql.Timestamp.from(occurredAt)
        );
    }
}
