-- Seed/patch OpenAI Standard per-1M-token prices based on OpenAI API pricing docs (2026-05-09).
-- Option A scope: token-priced models only (no tool-call, per-second, per-minute pricing).
--
-- This migration is idempotent: it only inserts missing rows (and updates two legacy rows whose
-- Standard prices changed in the official table).

-- Patch existing rows where present (keep validity window).
UPDATE provider_model_price
SET
    input_usd_per_million_tokens = 2.00,
    output_usd_per_million_tokens = 8.00
WHERE provider = 'OPENAI'
  AND model = 'gpt-4.1'
  AND valid_to IS NULL;

UPDATE provider_model_price
SET
    input_usd_per_million_tokens = 0.40,
    output_usd_per_million_tokens = 1.60
WHERE provider = 'OPENAI'
  AND model = 'gpt-4.1-mini'
  AND valid_to IS NULL;

-- Insert missing Standard rows (DEFAULT_VALID_FROM = 2024-01-01T00:00:00Z).
-- We key existence by (provider, model, valid_from, valid_to).
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
        ('OPENAI', 'gpt-5.5', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 5.00, 30.00),
        ('OPENAI', 'gpt-5.5-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 30.00, 180.00),
        ('OPENAI', 'gpt-5.4-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 30.00, 180.00),
        ('OPENAI', 'gpt-5.2', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.75, 14.00),
        ('OPENAI', 'gpt-5.2-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 21.00, 168.00),
        ('OPENAI', 'gpt-5.1', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.25, 10.00),
        ('OPENAI', 'gpt-5', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.25, 10.00),
        ('OPENAI', 'gpt-5-mini', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.25, 2.00),
        ('OPENAI', 'gpt-5-nano', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.05, 0.40),
        ('OPENAI', 'gpt-5-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 15.00, 120.00),

        ('OPENAI', 'o1', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 15.00, 60.00),
        ('OPENAI', 'o1-mini', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.10, 4.40),
        ('OPENAI', 'o1-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 150.00, 600.00),
        ('OPENAI', 'o3', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 2.00, 8.00),
        ('OPENAI', 'o3-mini', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.10, 4.40),
        ('OPENAI', 'o3-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 20.00, 80.00),
        ('OPENAI', 'o4-mini', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.10, 4.40),

        -- Snapshot IDs with distinct pricing.
        ('OPENAI', 'gpt-4o-2024-05-13', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 5.00, 15.00),
        ('OPENAI', 'gpt-4-turbo-2024-04-09', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 10.00, 30.00),
        ('OPENAI', 'gpt-4-0125-preview', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 10.00, 30.00),
        ('OPENAI', 'gpt-4-1106-preview', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 10.00, 30.00),
        ('OPENAI', 'gpt-4-1106-vision-preview', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 10.00, 30.00),
        ('OPENAI', 'gpt-4-0613', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 30.00, 60.00),
        ('OPENAI', 'gpt-4-0314', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 30.00, 60.00),
        ('OPENAI', 'gpt-4-32k', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 60.00, 120.00),

        -- Legacy GPT-3.5 and base models.
        ('OPENAI', 'gpt-3.5-turbo', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.50, 1.50),
        ('OPENAI', 'gpt-3.5-turbo-0125', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.50, 1.50),
        ('OPENAI', 'gpt-3.5-turbo-1106', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.00, 2.00),
        ('OPENAI', 'gpt-3.5-turbo-0613', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.50, 2.00),
        ('OPENAI', 'gpt-3.5-0301', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.50, 2.00),
        ('OPENAI', 'gpt-3.5-turbo-instruct', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.50, 2.00),
        ('OPENAI', 'gpt-3.5-turbo-16k-0613', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 3.00, 4.00),
        ('OPENAI', 'davinci-002', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 2.00, 2.00),
        ('OPENAI', 'babbage-002', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.40, 0.40)
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

