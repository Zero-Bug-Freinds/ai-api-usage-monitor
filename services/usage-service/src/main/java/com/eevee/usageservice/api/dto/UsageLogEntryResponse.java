package com.eevee.usageservice.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UsageLogEntryResponse(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String provider,
        String model,
        String apiKeyId,
        String apiKeyAlias,
        String apiKeyStatus,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        BigDecimal estimatedCost,
        String requestPath,
        String upstreamHost,
        Boolean streaming,
        boolean requestSuccessful,
        Integer upstreamStatusCode
) {
}