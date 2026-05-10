package com.zerobugfreinds.identity.events;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Platform user upsert notification consumed by team-service into {@code identity_user_sync}.
 * JSON field names and aliases match team-service deserialization expectations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityUserSyncEvent(
        @JsonProperty("eventType")
        @JsonAlias({"type"})
        String eventType,

        @JsonProperty("userId")
        @JsonAlias({"identityUserId", "id"})
        String userId,

        @JsonProperty("email")
        @JsonAlias({"userEmail"})
        String email,

        @JsonProperty("name")
        @JsonAlias({"displayName", "username"})
        String name,

        @JsonProperty("occurredAt")
        @JsonAlias({"createdAt", "updatedAt"})
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt
) {

    public IdentityUserSyncEvent {
        Objects.requireNonNull(userId, "userId");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        userId = userId.trim();
        if (eventType != null) {
            eventType = eventType.trim();
        }
        if (email != null) {
            email = email.trim();
        }
        if (name != null) {
            name = name.trim();
        }
    }

    public static IdentityUserSyncEvent of(
            String eventType,
            long userId,
            String email,
            String name,
            Instant occurredAt
    ) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        return new IdentityUserSyncEvent(
                eventType,
                Long.toString(userId),
                email != null ? email : "",
                name != null ? name : "",
                at
        );
    }
}
