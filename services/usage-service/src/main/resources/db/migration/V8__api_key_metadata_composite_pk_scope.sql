-- Composite PK (key_id, user_id, key_scope) isolates identity keys from team_api_key ids that collide numerically.
-- PERSONAL: team_id null. TEAM: one row per team member (user_id = member) for dashboard visibility.

ALTER TABLE api_key_metadata
    ADD COLUMN IF NOT EXISTS key_scope varchar(16);

UPDATE api_key_metadata
SET key_scope = CASE
    WHEN team_id IS NULL OR trim(team_id) = '' THEN 'PERSONAL'
    ELSE 'TEAM'
END
WHERE key_scope IS NULL;

ALTER TABLE api_key_metadata
    ALTER COLUMN key_scope SET NOT NULL;

ALTER TABLE api_key_metadata
    DROP CONSTRAINT IF EXISTS api_key_metadata_pkey;

ALTER TABLE api_key_metadata
    ADD PRIMARY KEY (key_id, user_id, key_scope);

CREATE INDEX IF NOT EXISTS idx_api_key_metadata_team_scope_status
    ON api_key_metadata (team_id, key_scope, status)
    WHERE team_id IS NOT NULL;
