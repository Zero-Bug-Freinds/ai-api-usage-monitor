ALTER TABLE api_key_metadata
    ADD COLUMN IF NOT EXISTS team_id varchar(255);

CREATE INDEX IF NOT EXISTS idx_api_key_metadata_team_id
    ON api_key_metadata (team_id);
