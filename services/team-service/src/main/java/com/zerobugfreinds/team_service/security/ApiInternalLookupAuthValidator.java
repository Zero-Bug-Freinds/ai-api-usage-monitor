package com.zerobugfreinds.team_service.security;

import com.zerobugfreinds.team_service.exception.InternalRequestUnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
            throw new InternalRequestUnauthorizedException("내부 API 키 조회 토큰이 서버에 설정되지 않았습니다");
        }
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new InternalRequestUnauthorizedException("내부 인증 토큰이 필요합니다");
        }
        String bearerToken = authorizationHeader.substring("Bearer ".length()).trim();
        if (!configuredToken.equals(bearerToken)) {
            throw new InternalRequestUnauthorizedException("내부 인증 토큰이 올바르지 않습니다");
        }
    }
}
