package com.zerobugfreinds.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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
		String alias,

		@DecimalMin(value = "0.0", inclusive = true, message = "monthlyBudgetUsd는 0 이상이어야 합니다")
		@Digits(integer = 10, fraction = 2, message = "monthlyBudgetUsd는 소수점 둘째 자리까지 입력할 수 있습니다")
		@NotNull(message = "monthlyBudgetUsd는 필수입니다")
		@JsonProperty("monthlyBudgetUsd")
		BigDecimal monthlyBudgetUsd
) {
}
