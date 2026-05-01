package com.zerobugfreinds.identity.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Event published when authenticated user's active context changes
 * (login or team context switch).
 */
public record UserContextChangedEvent(
		@JsonProperty("eventType")
		String eventType,

		@JsonProperty("schemaVersion")
		int schemaVersion,

		@JsonProperty("occurredAt")
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		Instant occurredAt,

		@JsonProperty("userId")
		Long userId,

		@JsonProperty("activeTeamId")
		Long activeTeamId,

		@JsonProperty("role")
		String role
) {
	public static final int CURRENT_SCHEMA_VERSION = 1;

	public static UserContextChangedEvent of(Long userId, Long activeTeamId, String role) {
		return new UserContextChangedEvent(
				IdentityExternalApiKeyEventTypes.USER_CONTEXT_CHANGED,
				CURRENT_SCHEMA_VERSION,
				Instant.now(),
				userId,
				activeTeamId,
				role
		);
	}
}
