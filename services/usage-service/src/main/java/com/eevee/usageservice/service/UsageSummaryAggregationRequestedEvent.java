package com.eevee.usageservice.service;

import com.eevee.usage.events.AiProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UsageSummaryAggregationRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        String teamId,
        String userId,
        AiProvider provider,
        String model,
        boolean requestSuccessful,
        Integer upstreamStatusCode,
        Long totalTokens,
        Long promptTokens,
        Long completionTokens,
        Long reasoningTokens,
        BigDecimal estimatedCost
) {
}
