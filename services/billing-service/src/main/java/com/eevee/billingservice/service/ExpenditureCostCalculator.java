package com.eevee.billingservice.service;

import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.usage.events.TokenUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes USD cost from token usage and per-million token prices.
 */
public final class ExpenditureCostCalculator {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    public record NormalizedTokens(long promptTokens, long completionTokens) {
    }

    private ExpenditureCostCalculator() {
    }

    /**
     * Aligns with cost rules: when prompt/completion are absent but total is present, split evenly for estimation.
     */
    public static NormalizedTokens normalizeTokens(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return new NormalizedTokens(0L, 0L);
        }
        long prompt = tokenUsage.promptTokens() != null ? tokenUsage.promptTokens() : 0L;
        long completion = tokenUsage.completionTokens() != null ? tokenUsage.completionTokens() : 0L;
        if (prompt == 0 && completion == 0) {
            Long total = tokenUsage.totalTokens();
            if (total != null && total > 0) {
                long half = total / 2;
                prompt = half;
                completion = total - half;
            }
        }
        return new NormalizedTokens(prompt, completion);
    }

    public static BigDecimal compute(TokenUsage tokenUsage, ProviderModelPriceEntity price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        NormalizedTokens nt = normalizeTokens(tokenUsage);
        long prompt = nt.promptTokens();
        long completion = nt.completionTokens();
        BigDecimal promptCost = BigDecimal.valueOf(prompt)
                .multiply(price.getInputUsdPerMillionTokens())
                .divide(MILLION, 10, RoundingMode.HALF_UP);
        BigDecimal completionCost = BigDecimal.valueOf(completion)
                .multiply(price.getOutputUsdPerMillionTokens())
                .divide(MILLION, 10, RoundingMode.HALF_UP);
        return promptCost.add(completionCost);
    }
}
