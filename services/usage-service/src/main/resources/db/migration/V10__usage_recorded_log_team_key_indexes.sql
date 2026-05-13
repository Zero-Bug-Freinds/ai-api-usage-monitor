-- Dashboard / member filters: team + logical team key + time range, and member + team + team key.
-- Note: CREATE INDEX CONCURRENTLY is not used here because Flyway wraps SQL migrations in a transaction by default.
-- For very large production tables, consider a follow-up Java migration (non-transactional) or a manual CONCURRENTLY build.

CREATE INDEX IF NOT EXISTS idx_url_team_team_api_key_occurred
    ON usage_recorded_log (team_id, team_api_key_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_url_user_team_team_api_key
    ON usage_recorded_log (user_id, team_id, team_api_key_id);
