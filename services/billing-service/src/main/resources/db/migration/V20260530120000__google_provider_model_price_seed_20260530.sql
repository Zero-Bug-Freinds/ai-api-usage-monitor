-- Seed Google Gemini Paid tier Standard per-1M-token prices (2026-05-30 snapshot).
-- Option A scope: representative Standard/Base input/output token pricing only.
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
        ('GOOGLE', 'gemini-3.1-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 2.00, 12.00),
        ('GOOGLE', 'gemini-2.5-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.25, 10.00),
        ('GOOGLE', 'gemini-2.5-flash', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.30, 2.50),
        ('GOOGLE', 'gemini-2.5-flash-lite', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.10, 0.40),
        ('GOOGLE', 'gemini-2.0-flash', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.10, 0.40),
        ('GOOGLE', 'gemini-2.0-flash-lite', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.10, 0.40),
        ('GOOGLE', 'gemini-3-flash', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.50, 3.00),
        ('GOOGLE', 'gemini-3-pro', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 2.00, 12.00),
        ('GOOGLE', 'gemini-3.1-flash-lite', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 0.25, 1.50),
        ('GOOGLE', 'gemini-3.5-flash', TIMESTAMPTZ '2024-01-01T00:00:00Z', NULL::timestamptz, 1.50, 9.00)
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
