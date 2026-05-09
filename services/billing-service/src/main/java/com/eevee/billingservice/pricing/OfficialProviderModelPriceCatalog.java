package com.eevee.billingservice.pricing;

import com.eevee.usage.events.AiProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Initial DB seed rows for {@code provider_model_price}: amounts are taken from the suppliers'
 * public pricing pages (snapshot documented below). Runtime source of truth remains the database;
 * this catalog is only used when the table is empty ({@code ProviderModelPriceSeed}).
 * <p>
 * When vendors change list prices, add new rows (or end-date old rows) via migration or ops —
 * do not encode USD amounts in enums.
 */
public final class OfficialProviderModelPriceCatalog {

    /**
     * Calendar date when the numbers below were checked against the official pages (YYYY-MM-DD).
     */
    public static final String DOCUMENTED_AS_OF = "2026-05-09";

    /** Google AI Gemini API pricing (Korean page). */
    public static final String REFERENCE_URL_GOOGLE_GEMINI = "https://ai.google.dev/gemini-api/docs/pricing?hl=ko";

    /** OpenAI API pricing table. */
    public static final String REFERENCE_URL_OPENAI = "https://openai.com/api/pricing";

    /**
     * OpenAI model matrix with per-model input/output USD (Standard tier, per 1M tokens).
     */
    public static final String REFERENCE_URL_OPENAI_PLATFORM_DOCS = "https://platform.openai.com/docs/pricing";

    /** Anthropic Claude API pricing (Korean docs). */
    public static final String REFERENCE_URL_ANTHROPIC = "https://platform.claude.com/docs/ko/about-claude/pricing";

    private static final Instant DEFAULT_VALID_FROM = Instant.parse("2024-01-01T00:00:00Z");

    public record CatalogRow(
            AiProvider provider,
            String modelId,
            BigDecimal inputUsdPerMillionTokens,
            BigDecimal outputUsdPerMillionTokens,
            Instant validFrom,
            String primaryReferenceUrl,
            String pricingNote
    ) {
    }

    private OfficialProviderModelPriceCatalog() {
    }

    /**
     * Rows inserted when {@code provider_model_price} is empty.
     * <ul>
     *   <li>Gemini: Gemini 2.5 Flash / Flash-Lite, <strong>Paid &gt; Standard</strong> text (and image/video where applicable).</li>
     *   <li>OpenAI: <strong>Standard</strong> tier per 1M tokens from platform pricing table ({@code gpt-4o-mini}, {@code gpt-5.4-nano}).</li>
     *   <li>Anthropic: <strong>Base input / output</strong> columns from the model pricing table (Claude Sonnet 4.x family).</li>
     * </ul>
     */
    public static List<CatalogRow> seedRows() {
        return List.of(
                new CatalogRow(
                        AiProvider.GOOGLE,
                        "gemini-3.1-pro",
                        new BigDecimal("2.00"),
                        new BigDecimal("12.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_GOOGLE_GEMINI,
                        "Gemini 3.1 Pro, Paid tier Standard, input/output USD per 1M tokens (see official Gemini pricing page)"
                ),
                new CatalogRow(
                        AiProvider.GOOGLE,
                        "gemini-2.5-pro",
                        new BigDecimal("1.25"),
                        new BigDecimal("10.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_GOOGLE_GEMINI,
                        "Gemini 2.5 Pro, Paid tier Standard, input/output USD per 1M tokens (see official Gemini pricing page)"
                ),
                new CatalogRow(
                        AiProvider.GOOGLE,
                        "gemini-2.5-flash",
                        new BigDecimal("0.30"),
                        new BigDecimal("2.50"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_GOOGLE_GEMINI,
                        "Gemini 2.5 Flash, Paid tier Standard, text/image/video input per 1M tokens; output per 1M tokens"
                ),
                new CatalogRow(
                        AiProvider.GOOGLE,
                        "gemini-2.5-flash-lite",
                        new BigDecimal("0.10"),
                        new BigDecimal("0.40"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_GOOGLE_GEMINI,
                        "Gemini 2.5 Flash-Lite, Paid tier Standard, text/image/video input per 1M tokens; output per 1M tokens"
                ),
                new CatalogRow(
                        AiProvider.GOOGLE,
                        "gemini-2.0-flash",
                        new BigDecimal("0.10"),
                        new BigDecimal("0.40"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_GOOGLE_GEMINI,
                        "Gemini 2.0 Flash (legacy), Paid tier Standard, input/output USD per 1M tokens (see official Gemini pricing page)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4o-mini",
                        new BigDecimal("0.15"),
                        new BigDecimal("0.60"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "gpt-4o-mini: Standard tier, input/output USD per 1M tokens (OpenAI API pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4o",
                        new BigDecimal("2.50"),
                        new BigDecimal("10.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "gpt-4o: Standard tier, input/output USD per 1M tokens (OpenAI API pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4.1",
                        new BigDecimal("2.00"),
                        new BigDecimal("8.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "gpt-4.1: Standard tier, input/output USD per 1M tokens (OpenAI API pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4.1-mini",
                        new BigDecimal("0.40"),
                        new BigDecimal("1.60"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "gpt-4.1-mini: Standard tier, input/output USD per 1M tokens (OpenAI API pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4.1-nano",
                        new BigDecimal("0.10"),
                        new BigDecimal("0.40"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "gpt-4.1-nano: Standard tier, input/output USD per 1M tokens (OpenAI API pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.5",
                        new BigDecimal("5.00"),
                        new BigDecimal("30.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5.5: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.5-pro",
                        new BigDecimal("30.00"),
                        new BigDecimal("180.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5.5-pro: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.4",
                        new BigDecimal("2.50"),
                        new BigDecimal("15.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "gpt-5.4: Standard tier, input/output USD per 1M tokens (OpenAI API pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.4-pro",
                        new BigDecimal("30.00"),
                        new BigDecimal("180.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5.4-pro: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.4-mini",
                        new BigDecimal("0.75"),
                        new BigDecimal("4.50"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "gpt-5.4-mini: Standard tier, input/output USD per 1M tokens (OpenAI API pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.4-nano",
                        new BigDecimal("0.20"),
                        new BigDecimal("1.25"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "gpt-5.4-nano: Standard tier, input/output USD per 1M tokens (OpenAI API pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.2",
                        new BigDecimal("1.75"),
                        new BigDecimal("14.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5.2: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.2-pro",
                        new BigDecimal("21.00"),
                        new BigDecimal("168.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5.2-pro: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.1",
                        new BigDecimal("1.25"),
                        new BigDecimal("10.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5.1: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5",
                        new BigDecimal("1.25"),
                        new BigDecimal("10.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5-mini",
                        new BigDecimal("0.25"),
                        new BigDecimal("2.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5-mini: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5-nano",
                        new BigDecimal("0.05"),
                        new BigDecimal("0.40"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5-nano: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5-pro",
                        new BigDecimal("15.00"),
                        new BigDecimal("120.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-5-pro: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "o1",
                        new BigDecimal("15.00"),
                        new BigDecimal("60.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "o1: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "o1-mini",
                        new BigDecimal("1.10"),
                        new BigDecimal("4.40"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "o1-mini: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "o1-pro",
                        new BigDecimal("150.00"),
                        new BigDecimal("600.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "o1-pro: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "o3",
                        new BigDecimal("2.00"),
                        new BigDecimal("8.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "o3: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "o3-mini",
                        new BigDecimal("1.10"),
                        new BigDecimal("4.40"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "o3-mini: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "o3-pro",
                        new BigDecimal("20.00"),
                        new BigDecimal("80.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "o3-pro: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "o4-mini",
                        new BigDecimal("1.10"),
                        new BigDecimal("4.40"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "o4-mini: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4o-2024-05-13",
                        new BigDecimal("5.00"),
                        new BigDecimal("15.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4o-2024-05-13: Standard tier snapshot model, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4-turbo-2024-04-09",
                        new BigDecimal("10.00"),
                        new BigDecimal("30.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4-turbo-2024-04-09: Standard tier snapshot model, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4-0125-preview",
                        new BigDecimal("10.00"),
                        new BigDecimal("30.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4-0125-preview: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4-1106-preview",
                        new BigDecimal("10.00"),
                        new BigDecimal("30.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4-1106-preview: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4-1106-vision-preview",
                        new BigDecimal("10.00"),
                        new BigDecimal("30.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4-1106-vision-preview: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4-0613",
                        new BigDecimal("30.00"),
                        new BigDecimal("60.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4-0613: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4-0314",
                        new BigDecimal("30.00"),
                        new BigDecimal("60.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4-0314: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-4-32k",
                        new BigDecimal("60.00"),
                        new BigDecimal("120.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4-32k: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-3.5-turbo",
                        new BigDecimal("0.50"),
                        new BigDecimal("1.50"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-3.5-turbo: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-3.5-turbo-0125",
                        new BigDecimal("0.50"),
                        new BigDecimal("1.50"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-3.5-turbo-0125: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-3.5-turbo-1106",
                        new BigDecimal("1.00"),
                        new BigDecimal("2.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-3.5-turbo-1106: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-3.5-turbo-0613",
                        new BigDecimal("1.50"),
                        new BigDecimal("2.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-3.5-turbo-0613: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-3.5-0301",
                        new BigDecimal("1.50"),
                        new BigDecimal("2.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-3.5-0301: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-3.5-turbo-instruct",
                        new BigDecimal("1.50"),
                        new BigDecimal("2.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-3.5-turbo-instruct: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-3.5-turbo-16k-0613",
                        new BigDecimal("3.00"),
                        new BigDecimal("4.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-3.5-turbo-16k-0613: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "davinci-002",
                        new BigDecimal("2.00"),
                        new BigDecimal("2.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "davinci-002: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "babbage-002",
                        new BigDecimal("0.40"),
                        new BigDecimal("0.40"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "babbage-002: Standard tier, input/output USD per 1M tokens (OpenAI API pricing docs)"
                ),
                new CatalogRow(
                        AiProvider.ANTHROPIC,
                        "claude-sonnet-4-20250514",
                        new BigDecimal("3.00"),
                        new BigDecimal("15.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_ANTHROPIC,
                        "Claude Sonnet 4.x — Base input $3/MTok, Output $15/MTok (API model id may vary; add rows for other IDs if needed)"
                ),
                new CatalogRow(
                        AiProvider.ANTHROPIC,
                        "claude-3-5-sonnet-20241022",
                        new BigDecimal("3.00"),
                        new BigDecimal("15.00"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_ANTHROPIC,
                        "Legacy Sonnet 3.5 dated snapshot id — same Base/Output as Sonnet family in current table until superseded"
                )
        );
    }
}
