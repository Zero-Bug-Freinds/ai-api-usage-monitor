package com.eevee.usage.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by {@code usage-service} after each persisted {@link UsageRecordedEvent}, once the
 * caller's KST-day cumulative token total (user + team + API key scope) has been updated in
 * {@code daily_cumulative_token_by_scope}.
 *
 * <p>Wire format: JSON camelCase. {@link #schemaVersion()} is set by the publisher.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyCumulativeTokensUpdatedEvent(
        int schemaVersion,
        UUID sourceEventId,
        long dailyTotalTokens,
        String userId,
        String teamId,
        String apiKeyId,
        String apiKeyAlias,
        Instant occurredAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public DailyCumulativeTokensUpdatedEvent {
        if (schemaVersion == 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + schemaVersion);
        }
        if (sourceEventId == null) {
            throw new IllegalArgumentException("sourceEventId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
    }
}
