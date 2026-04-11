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
    public static final String DOCUMENTED_AS_OF = "2026-04-11";

    /** Google AI Gemini API pricing (Korean page). */
    public static final String REFERENCE_URL_GOOGLE_GEMINI = "https://ai.google.dev/gemini-api/docs/pricing?hl=ko";

    /** OpenAI consumer API pricing (Korean). */
    public static final String REFERENCE_URL_OPENAI = "https://openai.com/ko-KR/api/pricing/";

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
                        AiProvider.OPENAI,
                        "gpt-4o-mini",
                        new BigDecimal("0.15"),
                        new BigDecimal("0.60"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI_PLATFORM_DOCS,
                        "gpt-4o-mini: Standard tier, input/output USD per 1M tokens (platform pricing table)"
                ),
                new CatalogRow(
                        AiProvider.OPENAI,
                        "gpt-5.4-nano",
                        new BigDecimal("0.20"),
                        new BigDecimal("1.25"),
                        DEFAULT_VALID_FROM,
                        REFERENCE_URL_OPENAI,
                        "GPT-5.4 nano: input/output USD per 1M tokens (openai.com API pricing, Standard)"
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
