CREATE INDEX IF NOT EXISTS idx_url_user_occurred
    ON usage_recorded_log (user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_url_team_occurred_desc
    ON usage_recorded_log (team_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_dus_team_user
    ON daily_usage_summary (team_id, user_id);
