package com.zerobugfreinds.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 찾기(재설정 메일 요청) 본문.
 */
public record ForgotPasswordRequest(
		@NotBlank @Email String email
) {
}
