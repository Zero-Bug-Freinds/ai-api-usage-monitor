package com.zerobugfreinds.identity_service.security;

import com.zerobugfreinds.identity_service.exception.InternalLookupUnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * POST /internal/v1/api-keys/lookup 등에 사용하는 공유 내부 Bearer 토큰 검증.
 */
@Component
public class ApiInternalLookupAuthValidator {

	private final String configuredToken;

	public ApiInternalLookupAuthValidator(
			@Value("${api.internal.key-lookup-token:}") String configuredToken
	) {
		this.configuredToken = configuredToken != null ? configuredToken.trim() : "";
	}

	public void validateBearer(String authorizationHeader) {
		if (!StringUtils.hasText(configuredToken)) {
			throw new InternalLookupUnauthorizedException("내부 API 키 조회 토큰이 서버에 설정되지 않았습니다");
		}
		if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
			throw new InternalLookupUnauthorizedException("내부 인증 토큰이 필요합니다");
		}
		String bearerToken = authorizationHeader.substring("Bearer ".length()).trim();
		if (!configuredToken.equals(bearerToken)) {
			throw new InternalLookupUnauthorizedException("내부 인증 토큰이 올바르지 않습니다");
		}
	}
}
