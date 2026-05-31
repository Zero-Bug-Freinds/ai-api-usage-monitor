package com.zerobugfreinds.ai_agent_service.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Purges agent-service projections for withdrawn Identity users.
 */
@Repository
public class AgentAccountDeletionJdbc {

	private final JdbcTemplate jdbcTemplate;

	public AgentAccountDeletionJdbc(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * @return deleted row counts per table in order documented below
	 */
	public int[] deleteAllProjectionsForUser(String userId) {
		String normalized = userId == null ? "" : userId.trim();
		if (normalized.isEmpty()) {
			return new int[8];
		}
		int identityKeys = jdbcTemplate.update(
				"""
						DELETE FROM identity_api_key_projection
						WHERE lower(trim(user_id)) = lower(trim(?))
						""",
				normalized
		);
		int billingSignals = jdbcTemplate.update(
				"""
						DELETE FROM billing_signal_projection
						WHERE lower(trim(user_id)) = lower(trim(?))
						""",
				normalized
		);
		int dailyTokens = jdbcTemplate.update(
				"""
						DELETE FROM daily_cumulative_token_projection
						WHERE lower(trim(user_id)) = lower(trim(?))
						""",
				normalized
		);
		int predictionSignals = jdbcTemplate.update(
				"""
						DELETE FROM usage_prediction_signal_projection
						WHERE lower(trim(user_id)) = lower(trim(?))
						""",
				normalized
		);
		int tokenRollups = jdbcTemplate.update(
				"""
						DELETE FROM usage_recorded_token_rollup
						WHERE scope_type = 'PERSONAL'
						  AND lower(trim(scope_id)) = lower(trim(?))
						""",
				normalized
		);
		int budgetForecasts = jdbcTemplate.update(
				"""
						DELETE FROM budget_forecast_projection
						WHERE scope_type = 'PERSONAL'
						  AND lower(trim(scope_id)) = lower(trim(?))
						""",
				normalized
		);
		int recommendations = jdbcTemplate.update(
				"""
						DELETE FROM recommendation_projection
						WHERE scope_type = 'PERSONAL'
						  AND lower(trim(scope_id)) = lower(trim(?))
						""",
				normalized
		);
		int teamKeysByOwner = jdbcTemplate.update(
				"""
						DELETE FROM team_api_key_projection
						WHERE lower(trim(owner_user_id)) = lower(trim(?))
						""",
				normalized
		);
		return new int[] {
				identityKeys,
				billingSignals,
				dailyTokens,
				predictionSignals,
				tokenRollups,
				budgetForecasts,
				recommendations,
				teamKeysByOwner
		};
	}
}
