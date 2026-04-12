package com.zerobugfreinds.identity_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 재설정 본문. 비밀번호 규칙은 {@link com.zerobugfreinds.identity_service.dto.SignupRequest} 와 동일하다.
 */
public record ResetPasswordRequest(
		@NotBlank @Size(max = 512) String token,
		@NotBlank
		@Size(min = 8, max = 100)
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[^a-zA-Z0-9])(?=\\S+$)[^A-Z]{8,100}$",
				message = "비밀번호는 소문자/숫자/특수문자를 각각 1개 이상 포함하고 대문자 없이 8~100자여야 합니다"
		)
		String password,
		@NotBlank String passwordConfirm
) {
}
