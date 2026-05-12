package com.zerobugfreinds.identity.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when external API key monthly budget changes.
 * This payload intentionally carries identification/metadata + budget only.
 */
public record ExternalApiKeyBudgetChangedEvent(
		@JsonProperty("eventType")
		String eventType,

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
		String userId,

		@JsonProperty("visibility")
		String visibility,

		@JsonProperty("provider")
		String provider,

		@JsonProperty("status")
		ExternalApiKeyStatus status,

		@JsonProperty("monthlyBudgetUsd")
		BigDecimal monthlyBudgetUsd,

		@JsonProperty("keyHash")
		String keyHash
) {
	public static final int CURRENT_SCHEMA_VERSION = 2;

	public static ExternalApiKeyBudgetChangedEvent of(
			Long keyId,
			String alias,
			String userId,
			String provider,
			ExternalApiKeyStatus status,
			BigDecimal monthlyBudgetUsd,
			String keyHash
	) {
		return new ExternalApiKeyBudgetChangedEvent(
				IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_BUDGET_CHANGED,
				CURRENT_SCHEMA_VERSION,
				Instant.now(),
				keyId,
				alias,
				userId,
				"PRIVATE",
				provider,
				status,
				monthlyBudgetUsd,
				keyHash
		);
	}
}
