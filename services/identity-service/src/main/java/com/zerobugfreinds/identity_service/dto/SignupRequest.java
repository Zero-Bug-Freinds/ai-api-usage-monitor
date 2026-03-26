package com.zerobugfreinds.identity_service.dto;

import com.zerobugfreinds.identity_service.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 본문.
 */
public record SignupRequest(
		@NotBlank @Email String email,
		@NotBlank
		@Size(min = 8, max = 100)
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[^a-zA-Z0-9])(?=\\S+$)[^A-Z]{8,100}$",
				message = "비밀번호는 소문자/숫자/특수문자를 각각 1개 이상 포함하고 대문자 없이 8~100자여야 합니다"
		)
		String password,
		@NotBlank String passwordConfirm,
		@NotBlank @Size(max = 100) String name,
		@NotNull Role role
) {
}
