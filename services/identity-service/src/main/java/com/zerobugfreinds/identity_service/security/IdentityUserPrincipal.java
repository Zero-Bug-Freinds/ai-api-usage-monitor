package com.zerobugfreinds.identity_service.security;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * JWT 인증 성공 시 SecurityContext 에 담기는 로그인 사용자 정보.
 */
public record IdentityUserPrincipal(Long userId, String email, String role) implements AuthenticatedPrincipal {

	@Override
	public String getName() {
		return email;
	}
}
