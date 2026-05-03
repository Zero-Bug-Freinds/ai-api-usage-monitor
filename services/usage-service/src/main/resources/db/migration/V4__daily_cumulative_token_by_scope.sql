-- KST calendar day + user + team + API key grain: atomic running total of total_tokens (Task36-1).
CREATE TABLE IF NOT EXISTS daily_cumulative_token_by_scope (
    usage_date date NOT NULL,
    user_id varchar(255) NOT NULL,
    team_id varchar(255) NOT NULL DEFAULT '',
    api_key_id varchar(255) NOT NULL DEFAULT '',
    total_tokens bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_daily_cumulative_token_by_scope PRIMARY KEY (usage_date, user_id, team_id, api_key_id)
);

CREATE INDEX IF NOT EXISTS idx_dctbs_date_user
    ON daily_cumulative_token_by_scope (usage_date, user_id);

-- Idempotency: one rollup increment per usage_recorded_log.event_id
CREATE TABLE IF NOT EXISTS processed_daily_cumulative_token_event (
    event_id uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);

-- Align with usage_recorded_log (same KST date / COALESCE rules as daily_usage_summary backfill)
INSERT INTO daily_cumulative_token_by_scope (
    usage_date,
    user_id,
    team_id,
    api_key_id,
    total_tokens,
    updated_at
)
SELECT
    (u.occurred_at AT TIME ZONE 'Asia/Seoul')::date AS usage_date,
    u.user_id,
    COALESCE(u.team_id, '') AS team_id,
    COALESCE(u.api_key_id, '') AS api_key_id,
    COALESCE(SUM(COALESCE(u.total_tokens, 0)), 0)::bigint AS total_tokens,
    now() AS updated_at
FROM usage_recorded_log u
GROUP BY
    (u.occurred_at AT TIME ZONE 'Asia/Seoul')::date,
    u.user_id,
    COALESCE(u.team_id, ''),
    COALESCE(u.api_key_id, '')
ON CONFLICT (usage_date, user_id, team_id, api_key_id) DO UPDATE SET
    total_tokens = EXCLUDED.total_tokens,
    updated_at = now();
