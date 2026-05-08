-- Monthly expenditure aggregated by team API key (teamApiKeyId).
-- This allows "team registered keys only" spend reporting and team budget threshold logic.

CREATE TABLE IF NOT EXISTS team_api_key_monthly_expenditure_agg (
    month_start_date DATE NOT NULL,
    team_api_key_id BIGINT NOT NULL,
    total_cost_usd NUMERIC(19, 6) NOT NULL,
    is_finalized BOOLEAN NOT NULL DEFAULT FALSE,
    finalized_at TIMESTAMPTZ NULL,
    PRIMARY KEY (month_start_date, team_api_key_id)
);

