package com.zerobugfreinds.identity_service.dto;

import jakarta.validation.constraints.Size;

/**
 * 인증된 사용자 프로필(이메일·표시 이름) 부분 갱신 요청.
 * 둘 다 생략·공백이면 400으로 거절한다. 이메일 형식은 서비스 계층에서 검증한다.
 */
public record UpdateProfileRequest(
		@Size(max = 255)
		String email,

		@Size(max = 100)
		String name
) {
}
