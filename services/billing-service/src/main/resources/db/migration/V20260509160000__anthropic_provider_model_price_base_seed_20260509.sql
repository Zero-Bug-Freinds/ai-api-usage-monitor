-- Seed/patch Anthropic (Claude) Base per-1M-token prices based on Anthropic pricing table (2026-05-09).
-- Option A scope: base input/output token pricing only (no caching multipliers, batch discounts, data residency multipliers).
--
-- Strategy: insert both "alias" and "dated" model IDs where applicable so billing can match
-- whatever model string is observed from upstream responses.
--
-- Idempotent: inserts rows that do not exist for the same (provider, model, valid_from, valid_to).

INSERT INTO provider_model_price (
    provider,
    model,
    valid_from,
    valid_to,
    input_usd_per_million_tokens,
    output_usd_per_million_tokens
)
SELECT v.provider,
       v.model,
       v.valid_from,
       v.valid_to,
       v.input_usd_per_million_tokens,
       v.output_usd_per_million_tokens
FROM (
    VALUES
        -- Opus
        ('ANTHROPIC', 'claude-opus-4-7', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 5.00, 25.00),
        ('ANTHROPIC', 'claude-opus-4-6', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 5.00, 25.00),
        ('ANTHROPIC', 'claude-opus-4-5-20251101', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 5.00, 25.00),
        ('ANTHROPIC', 'claude-opus-4-1-20250805', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 15.00, 75.00),

        -- Sonnet
        ('ANTHROPIC', 'claude-sonnet-4-6', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 3.00, 15.00),
        ('ANTHROPIC', 'claude-sonnet-4-5', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 3.00, 15.00),
        ('ANTHROPIC', 'claude-sonnet-4-5-20250929', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 3.00, 15.00),
        ('ANTHROPIC', 'claude-sonnet-4', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 3.00, 15.00),
        ('ANTHROPIC', 'claude-sonnet-4-20250514', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 3.00, 15.00),

        -- Haiku
        ('ANTHROPIC', 'claude-haiku-4-5', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 1.00, 5.00),
        ('ANTHROPIC', 'claude-haiku-4-5-20251001', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 1.00, 5.00),
        ('ANTHROPIC', 'claude-haiku-3-5', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 0.80, 4.00),
        ('ANTHROPIC', 'claude-haiku-3', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL, 0.25, 1.25)
) AS v(
    provider,
    model,
    valid_from,
    valid_to,
    input_usd_per_million_tokens,
    output_usd_per_million_tokens
)
WHERE NOT EXISTS (
    SELECT 1
    FROM provider_model_price p
    WHERE p.provider = v.provider
      AND p.model = v.model
      AND p.valid_from = v.valid_from
      AND (
          (p.valid_to IS NULL AND v.valid_to IS NULL)
          OR (p.valid_to = v.valid_to)
      )
);

