package com.eevee.billingservice.service;

import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpenditureCostCalculatorTest {

    @Test
    void compute_usesPromptAndCompletion() {
        ProviderModelPriceEntity price = new ProviderModelPriceEntity(
                AiProvider.OPENAI,
                "m",
                Instant.EPOCH,
                null,
                new BigDecimal("1.00"),
                new BigDecimal("2.00")
        );
        TokenUsage tu = new TokenUsage("m", 1_000_000L, 500_000L, 1_500_000L);
        BigDecimal cost = ExpenditureCostCalculator.compute(tu, price);
        assertEquals(0, cost.compareTo(new BigDecimal("2.0")));
    }

    @Test
    void normalize_splitsTotalWhenBreakdownMissing() {
        TokenUsage tu = new TokenUsage("m", null, null, 10L);
        ExpenditureCostCalculator.NormalizedTokens nt = ExpenditureCostCalculator.normalizeTokens(tu);
        assertEquals(10L, nt.promptTokens() + nt.completionTokens());
    }
}
