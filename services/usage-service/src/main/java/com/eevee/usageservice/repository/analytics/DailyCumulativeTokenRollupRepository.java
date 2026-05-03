package com.eevee.usageservice.repository.analytics;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Atomic KST-day rollup for {@code daily_cumulative_token_by_scope}, with per-event idempotency
 * via {@code processed_daily_cumulative_token_event}.
 */
@Repository
public class DailyCumulativeTokenRollupRepository {

    private static final String INSERT_PROCESSED =
            "INSERT INTO processed_daily_cumulative_token_event (event_id) VALUES (?::uuid) ON CONFLICT DO NOTHING";

    private static final String UPSERT_INCREMENT_RETURNING = """
            INSERT INTO daily_cumulative_token_by_scope (usage_date, user_id, team_id, api_key_id, total_tokens, updated_at)
            VALUES (?, ?, ?, ?, ?, now())
            ON CONFLICT (usage_date, user_id, team_id, api_key_id) DO UPDATE SET
                total_tokens = daily_cumulative_token_by_scope.total_tokens + EXCLUDED.total_tokens,
                updated_at = now()
            RETURNING total_tokens
            """;

    private final JdbcTemplate jdbc;

    public DailyCumulativeTokenRollupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return {@code true} if this event id was newly claimed (insert succeeded)
     */
    public boolean tryClaimProcessedEvent(UUID eventId) {
        int inserted = jdbc.update(INSERT_PROCESSED, eventId);
        return inserted == 1;
    }

    public long incrementAndReturnTotal(
            LocalDate usageDateKst,
            String userId,
            String teamIdKey,
            String apiKeyIdKey,
            long deltaTokens
    ) {
        if (deltaTokens < 0) {
            deltaTokens = 0;
        }
        long delta = deltaTokens;
        ConnectionCallback<Long> callback = (Connection connection) -> {
            try (PreparedStatement ps = connection.prepareStatement(UPSERT_INCREMENT_RETURNING)) {
                ps.setObject(1, usageDateKst);
                ps.setString(2, userId);
                ps.setString(3, teamIdKey);
                ps.setString(4, apiKeyIdKey);
                ps.setLong(5, delta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("rollup upsert returned no row");
                    }
                    return rs.getLong(1);
                }
            }
        };
        return jdbc.execute(callback);
    }
}
