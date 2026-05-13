-- Team API key read model for billing (synced from team-service TEAM_API_KEY_STATUS_CHANGED events).

CREATE TABLE IF NOT EXISTS billing_team_api_key (
    team_api_key_id BIGINT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    alias TEXT NOT NULL,
    provider TEXT NOT NULL,
    monthly_budget_usd NUMERIC(19, 6) NOT NULL,
    status TEXT NOT NULL,
    retain_logs BOOLEAN NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Idempotency ledger for inbound team API key status events (billing-service).
CREATE TABLE IF NOT EXISTS billing_team_api_key_event_processed (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

