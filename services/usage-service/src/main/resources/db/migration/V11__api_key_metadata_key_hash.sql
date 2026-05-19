-- Identity keyHash and usage fingerprint for credential-level filter grouping (Task55-15).
ALTER TABLE api_key_metadata
    ADD COLUMN IF NOT EXISTS key_hash varchar(64) NULL;

CREATE INDEX IF NOT EXISTS idx_api_key_metadata_user_scope_provider_key_hash
    ON api_key_metadata (user_id, key_scope, provider, key_hash)
    WHERE key_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_api_key_metadata_team_scope_provider_key_hash
    ON api_key_metadata (team_id, key_scope, provider, key_hash)
    WHERE team_id IS NOT NULL AND key_hash IS NOT NULL;
