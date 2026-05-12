-- Daily expenditure aggregated by team API key (teamApiKeyId).
-- This enables accurate range queries (last 7/30/90 days, custom ranges) for team registered keys.

CREATE TABLE IF NOT EXISTS team_api_key_daily_expenditure_agg (
    agg_date DATE NOT NULL,
    team_api_key_id BIGINT NOT NULL,
    total_cost_usd NUMERIC(19, 6) NOT NULL,
    PRIMARY KEY (agg_date, team_api_key_id)
);

CREATE INDEX IF NOT EXISTS idx_team_api_key_daily_expenditure_agg_team_api_key_id
    ON team_api_key_daily_expenditure_agg (team_api_key_id);

