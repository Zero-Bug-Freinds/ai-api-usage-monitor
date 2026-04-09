package com.zerobugfreinds.team_service.security;

import org.springframework.security.core.AuthenticatedPrincipal;

public record TeamUserPrincipal(String userId, String role) implements AuthenticatedPrincipal {
	@Override
	public String getName() {
		return userId;
	}
}
