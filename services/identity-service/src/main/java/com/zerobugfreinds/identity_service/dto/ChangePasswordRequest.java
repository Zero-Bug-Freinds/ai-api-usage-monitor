package com.zerobugfreinds.identity_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 로그인된 사용자의 비밀번호 변경 요청.
 */
public record ChangePasswordRequest(
		@NotBlank(message = "현재 비밀번호를 입력해주세요")
		@Size(min = 8, max = 100)
		String currentPassword,
		@NotBlank
		@Size(min = 8, max = 100)
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[^a-zA-Z0-9])(?=\\S+$)[^A-Z]{8,100}$",
				message = "비밀번호는 소문자/숫자/특수문자를 각각 1개 이상 포함하고 대문자 없이 8~100자여야 합니다"
		)
		String newPassword,
		@NotBlank(message = "새 비밀번호 확인을 입력해주세요")
		String newPasswordConfirm
) {
}
