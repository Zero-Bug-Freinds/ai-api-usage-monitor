package com.zerobugfreinds.identity.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Event published when external API key metadata or lifecycle status changes
 * (등록·수정·삭제 예약·취소). 물리 삭제 완료는 {@link ExternalApiKeyDeletedEvent} 로 별도 발행한다.
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

		@JsonProperty("visibility")
		String visibility,

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
				"PRIVATE",
				provider,
				status
		);
	}
}
