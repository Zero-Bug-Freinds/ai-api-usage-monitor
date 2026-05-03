package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;

public record ProviderModelCostTokenRow(
        String provider,
        String model,
        BigDecimal totalCostUsd,
        long totalTokens
) {
}
