package com.zerobugfreinds.identity.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 개인 외부 API Key가 물리 삭제될 때 usage-service 등이 소비하는 표준형 이벤트.
 * team-service 의 {@code TeamApiKeyDeletedEvent} 필드 구성(식별·시각·유형 문자열)에 맞춘다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExternalApiKeyDeletedEvent(
		@JsonProperty("eventType")
		String eventType,

		@JsonProperty("userId")
		Long userId,

		@JsonProperty("apiKeyId")
		Long apiKeyId,

		@JsonProperty("occurredAt")
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		Instant occurredAt,

		@JsonProperty("retainLogs")
		boolean retainLogs,

		@JsonProperty("provider")
		String provider,

		@JsonProperty("alias")
		String alias
) {
	public static ExternalApiKeyDeletedEvent of(
			Long userId,
			Long apiKeyId,
			Instant occurredAt,
			boolean retainLogs,
			String provider,
			String alias
	) {
		return new ExternalApiKeyDeletedEvent(
				IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED,
				userId,
				apiKeyId,
				occurredAt,
				retainLogs,
				provider,
				alias
		);
	}
}
