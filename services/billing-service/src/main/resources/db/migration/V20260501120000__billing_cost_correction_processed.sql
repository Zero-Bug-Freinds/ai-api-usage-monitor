-- Idempotency ledger for inbound cost corrections (billing-service).
-- Hibernate `ddl-auto` can create this table in dev; keep this script for explicit DBA/Flyway-style rollouts.

CREATE TABLE IF NOT EXISTS billing_cost_correction_processed (
    correction_event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
