ALTER TABLE usage_recorded_log
    ADD COLUMN IF NOT EXISTS team_api_key_id varchar(255);

