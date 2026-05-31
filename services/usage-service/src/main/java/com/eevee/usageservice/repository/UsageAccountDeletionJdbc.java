package com.eevee.usageservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Personal-scope purge for Identity account deletion (team-owned rows are excluded).
 */
@Repository
public class UsageAccountDeletionJdbc {

    private static final String PERSONAL_TEAM_FILTER = "(team_id IS NULL OR trim(team_id) = '')";

    private final JdbcTemplate jdbcTemplate;

    public UsageAccountDeletionJdbc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return deleted row counts
     *         {@code [usage_recorded_log, api_key_metadata, daily_usage_summary, daily_cumulative_token_by_scope]}
     */
    public int[] deletePersonalDataForUser(String userId) {
        int logs = jdbcTemplate.update(
                """
                        DELETE FROM usage_recorded_log
                        WHERE lower(trim(user_id)) = lower(trim(?))
                          AND %s
                        """.formatted(PERSONAL_TEAM_FILTER),
                userId
        );
        int metadata = jdbcTemplate.update(
                """
                        DELETE FROM api_key_metadata
                        WHERE lower(trim(user_id)) = lower(trim(?))
                          AND key_scope = 'PERSONAL'
                          AND %s
                        """.formatted(PERSONAL_TEAM_FILTER),
                userId
        );
        int summary = jdbcTemplate.update(
                """
                        DELETE FROM daily_usage_summary
                        WHERE lower(trim(user_id)) = lower(trim(?))
                          AND %s
                        """.formatted(PERSONAL_TEAM_FILTER),
                userId
        );
        int cumulative = jdbcTemplate.update(
                """
                        DELETE FROM daily_cumulative_token_by_scope
                        WHERE lower(trim(user_id)) = lower(trim(?))
                          AND %s
                        """.formatted(PERSONAL_TEAM_FILTER),
                userId
        );
        return new int[] {logs, metadata, summary, cumulative};
    }
}
