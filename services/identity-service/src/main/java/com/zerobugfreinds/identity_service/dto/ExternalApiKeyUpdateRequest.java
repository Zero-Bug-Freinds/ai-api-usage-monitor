package com.zerobugfreinds.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 외부 API 키 수정 요청. JSON 필드명은 {@code provider}, {@code externalKey}, {@code alias}.
 */
public record ExternalApiKeyUpdateRequest(
		ExternalApiKeyProvider provider,

		@Size(max = 4096)
		@JsonProperty("externalKey")
		String externalKey,

		@NotBlank(message = "alias는 필수입니다")
		@Size(max = 100)
		@JsonProperty("alias")
		String alias
) {
}
