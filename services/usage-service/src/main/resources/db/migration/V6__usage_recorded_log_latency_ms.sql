ALTER TABLE usage_recorded_log
    ADD COLUMN IF NOT EXISTS latency_ms bigint;

