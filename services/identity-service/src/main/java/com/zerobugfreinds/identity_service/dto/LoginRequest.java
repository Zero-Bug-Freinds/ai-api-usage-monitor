package com.zerobugfreinds.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 본문.
 */
public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, max = 100) String password
) {
}
