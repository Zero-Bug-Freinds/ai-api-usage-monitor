CREATE TABLE IF NOT EXISTS api_key_metadata (
    key_id varchar(255) PRIMARY KEY,
    user_id varchar(255) NOT NULL,
    provider varchar(255),
    alias varchar(255),
    status varchar(255) NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS usage_recorded_log (
    event_id uuid PRIMARY KEY,
    occurred_at timestamptz NOT NULL,
    correlation_id varchar(255),
    user_id varchar(255) NOT NULL,
    organization_id varchar(255),
    team_id varchar(255),
    api_key_id varchar(255),
    api_key_fingerprint varchar(255),
    api_key_source varchar(255),
    provider varchar(255) NOT NULL,
    model varchar(255),
    prompt_tokens bigint,
    completion_tokens bigint,
    total_tokens bigint,
    estimated_reasoning_tokens bigint,
    provider_token_details jsonb,
    estimated_cost numeric(18, 10),
    request_path varchar(255),
    upstream_host varchar(255),
    streaming boolean,
    request_successful boolean NOT NULL DEFAULT true,
    upstream_status_code integer,
    persisted_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_url_team_occurred
    ON usage_recorded_log (team_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_url_user_team_occurred
    ON usage_recorded_log (user_id, team_id, occurred_at);
