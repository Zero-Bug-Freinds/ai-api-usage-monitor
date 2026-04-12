package com.zerobugfreinds.team_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamApiKeySummaryResponse(
        Long id,
        String provider,
        String alias,
        @JsonProperty("monthlyBudgetUsd") BigDecimal monthlyBudgetUsd,
        Instant createdAt,
        Instant deletionRequestedAt,
        Instant permanentDeletionAt,
        Integer deletionGraceDays
) {
}
