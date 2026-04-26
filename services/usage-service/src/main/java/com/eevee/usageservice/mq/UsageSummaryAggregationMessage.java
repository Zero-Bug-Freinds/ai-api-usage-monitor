package com.eevee.usageservice.mq;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UsageSummaryAggregationMessage(
        UUID eventId,
        Instant occurredAt,
        String teamId,
        String userId,
        String provider,
        String model,
        long requestCount,
        long successCount,
        long errorCount,
        long totalTokens,
        long promptTokens,
        long completionTokens,
        long reasoningTokens,
        BigDecimal totalCost
) {
}
