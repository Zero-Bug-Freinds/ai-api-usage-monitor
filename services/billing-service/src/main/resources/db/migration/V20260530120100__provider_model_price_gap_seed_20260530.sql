-- Seed Anthropic/OpenAI gap rows from 2026-05-30 pricing audit.
-- Option A scope: base input/output token pricing only.
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
        -- Anthropic Opus gaps (alias + dated snapshot ids)
        ('ANTHROPIC', 'claude-opus-4-8', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 5.00, 25.00),
        ('ANTHROPIC', 'claude-opus-4-5', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 5.00, 25.00),
        ('ANTHROPIC', 'claude-opus-4-0', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 15.00, 75.00),
        ('ANTHROPIC', 'claude-opus-4-20250514', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 15.00, 75.00),

        -- Legacy Sonnet 3.5 dated id (present in Java catalog, missing from prior SQL seed)
        ('ANTHROPIC', 'claude-3-5-sonnet-20241022', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 3.00, 15.00)
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
