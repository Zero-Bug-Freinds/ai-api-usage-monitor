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
		Long userId,

		@JsonProperty("visibility")
		String visibility,

		@JsonProperty("provider")
		String provider,

		@JsonProperty("status")
		ExternalApiKeyStatus status,

		@JsonProperty("monthlyBudgetUsd")
		BigDecimal monthlyBudgetUsd
) {
	public static final int CURRENT_SCHEMA_VERSION = 1;

	public static ExternalApiKeyBudgetChangedEvent of(
			Long keyId,
			String alias,
			Long userId,
			String provider,
			ExternalApiKeyStatus status,
			BigDecimal monthlyBudgetUsd
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
				monthlyBudgetUsd
		);
	}
}
