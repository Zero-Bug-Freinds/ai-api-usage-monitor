package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by Proxy Service when usage is known (or partially known) after an upstream call.
 * Consumers: Usage Tracking, Billing, Analytics, Quota.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageRecordedEvent(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String userId,
        String organizationId,
        String teamId,
        String apiKeyId,
        String teamApiKeyId,
        String apiKeyFingerprint,
        String apiKeySource,
        AiProvider provider,
        String model,
        @JsonAlias({"token_usage"}) TokenUsage tokenUsage,
        BigDecimal estimatedCost,
        String requestPath,
        String upstreamHost,
        Long latencyMs,
        Boolean streaming,
        Boolean requestSuccessful,
        Integer upstreamStatusCode
) {
    public UsageRecordedEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (requestSuccessful == null) {
            requestSuccessful = Boolean.TRUE;
        }
    }

    public UsageRecordedEvent(
            UUID eventId,
            Instant occurredAt,
            String correlationId,
            String userId,
            String organizationId,
            String teamId,
            String apiKeyId,
            String teamApiKeyId,
            String apiKeyFingerprint,
            String apiKeySource,
            AiProvider provider,
            String model,
            TokenUsage tokenUsage,
            BigDecimal estimatedCost,
            String requestPath,
            String upstreamHost,
            Boolean streaming,
            Boolean requestSuccessful,
            Integer upstreamStatusCode
    ) {
        this(
                eventId,
                occurredAt,
                correlationId,
                userId,
                organizationId,
                teamId,
                apiKeyId,
                teamApiKeyId,
                apiKeyFingerprint,
                apiKeySource,
                provider,
                model,
                tokenUsage,
                estimatedCost,
                requestPath,
                upstreamHost,
                null,
                streaming,
                requestSuccessful,
                upstreamStatusCode
        );
    }
}