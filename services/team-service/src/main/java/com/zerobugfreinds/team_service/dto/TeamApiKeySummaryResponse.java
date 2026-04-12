package com.zerobugfreinds.team_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record TeamApiKeySummaryResponse(
        Long id,
        String provider,
        String alias,
        String keyPreview,
        @JsonProperty("monthlyBudgetUsd") BigDecimal monthlyBudgetUsd,
        Instant createdAt
) {
}
