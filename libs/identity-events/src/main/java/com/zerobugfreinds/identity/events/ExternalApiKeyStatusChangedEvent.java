package com.zerobugfreinds.identity.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Event published when external API key metadata or lifecycle status changes.
 * Budget fields are intentionally excluded.
 */
public record ExternalApiKeyStatusChangedEvent(
		@JsonProperty("schemaVersion")
		int schemaVersion,

		@JsonProperty("occurredAt")
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		Instant occurredAt,

		@JsonProperty("keyId")
		Long keyId,

		@JsonProperty("alias")
		String alias,

		@JsonProperty("userId")
		Long userId,

		@JsonProperty("provider")
		String provider,

		@JsonProperty("status")
		ExternalApiKeyStatus status
) {
	public static final int CURRENT_SCHEMA_VERSION = 1;

	public static ExternalApiKeyStatusChangedEvent of(
			Long keyId,
			String alias,
			Long userId,
			String provider,
			ExternalApiKeyStatus status
	) {
		return new ExternalApiKeyStatusChangedEvent(
				CURRENT_SCHEMA_VERSION,
				Instant.now(),
				keyId,
				alias,
				userId,
				provider,
				status
		);
	}
}
