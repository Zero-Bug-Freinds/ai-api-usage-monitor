-- Price table for computing billable cost from token usage.

CREATE TABLE IF NOT EXISTS provider_model_price (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(512) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NULL,
    input_usd_per_million_tokens NUMERIC(24, 10) NOT NULL,
    output_usd_per_million_tokens NUMERIC(24, 10) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_provider_model_price_provider_model_valid
    ON provider_model_price (provider, model, valid_from, valid_to);

CREATE INDEX IF NOT EXISTS idx_provider_model_price_lookup
    ON provider_model_price (provider, model, valid_from);

