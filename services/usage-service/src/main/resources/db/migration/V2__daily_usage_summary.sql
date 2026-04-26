CREATE TABLE IF NOT EXISTS daily_usage_summary (
    usage_date date NOT NULL,
    team_id varchar(255) NOT NULL DEFAULT '',
    user_id varchar(255) NOT NULL,
    model varchar(255) NOT NULL,
    provider varchar(64) NOT NULL,
    request_count bigint NOT NULL DEFAULT 0,
    success_count bigint NOT NULL DEFAULT 0,
    error_count bigint NOT NULL DEFAULT 0,
    total_tokens bigint NOT NULL DEFAULT 0,
    prompt_tokens bigint NOT NULL DEFAULT 0,
    completion_tokens bigint NOT NULL DEFAULT 0,
    reasoning_tokens bigint NOT NULL DEFAULT 0,
    total_cost numeric(18, 10) NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_daily_usage_summary PRIMARY KEY (usage_date, team_id, user_id, model, provider)
);

CREATE INDEX IF NOT EXISTS idx_dus_date_team_user
    ON daily_usage_summary (usage_date, team_id, user_id);

CREATE TABLE IF NOT EXISTS processed_summary_event (
    event_id uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO daily_usage_summary (
    usage_date,
    team_id,
    user_id,
    model,
    provider,
    request_count,
    success_count,
    error_count,
    total_tokens,
    prompt_tokens,
    completion_tokens,
    reasoning_tokens,
    total_cost,
    updated_at
)
SELECT
    (u.occurred_at AT TIME ZONE 'Asia/Seoul')::date AS usage_date,
    COALESCE(u.team_id, '') AS team_id,
    u.user_id,
    COALESCE(NULLIF(TRIM(u.model), ''), LOWER(u.provider::text) || '_unknown') AS model,
    u.provider::text AS provider,
    COUNT(*)::bigint AS request_count,
    COALESCE(SUM(CASE WHEN u.request_successful THEN 1 ELSE 0 END), 0)::bigint AS success_count,
    COALESCE(SUM(CASE WHEN (NOT u.request_successful OR (u.upstream_status_code IS NOT NULL AND u.upstream_status_code >= 400)) THEN 1 ELSE 0 END), 0)::bigint AS error_count,
    COALESCE(SUM(u.total_tokens), 0)::bigint AS total_tokens,
    COALESCE(SUM(u.prompt_tokens), 0)::bigint AS prompt_tokens,
    COALESCE(SUM(u.completion_tokens), 0)::bigint AS completion_tokens,
    COALESCE(SUM(u.estimated_reasoning_tokens), 0)::bigint AS reasoning_tokens,
    COALESCE(SUM(u.estimated_cost), 0) AS total_cost,
    now() AS updated_at
FROM usage_recorded_log u
GROUP BY
    (u.occurred_at AT TIME ZONE 'Asia/Seoul')::date,
    COALESCE(u.team_id, ''),
    u.user_id,
    COALESCE(NULLIF(TRIM(u.model), ''), LOWER(u.provider::text) || '_unknown'),
    u.provider::text
ON CONFLICT (usage_date, team_id, user_id, model, provider) DO UPDATE SET
    request_count = EXCLUDED.request_count,
    success_count = EXCLUDED.success_count,
    error_count = EXCLUDED.error_count,
    total_tokens = EXCLUDED.total_tokens,
    prompt_tokens = EXCLUDED.prompt_tokens,
    completion_tokens = EXCLUDED.completion_tokens,
    reasoning_tokens = EXCLUDED.reasoning_tokens,
    total_cost = EXCLUDED.total_cost,
    updated_at = now();
