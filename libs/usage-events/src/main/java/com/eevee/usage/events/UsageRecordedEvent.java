package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by Proxy Service when usage is known (or partially known) after an upstream call.
 * Consumers: Usage Tracking, Billing, Analytics, Quota.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageRecordedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String userId,
        String organizationId,
        String teamId,
        AiProvider provider,
        String model,
        TokenUsage tokenUsage,
        BigDecimal estimatedCost,
        String requestPath,
        String upstreamHost,
        Boolean streaming
) {
    public UsageRecordedEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
