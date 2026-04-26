package com.zerobugfreinds.identity_service.dto;

/**
 * 액세스/리프레시 토큰 세트 응답.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        long refreshExpiresInSeconds
) {
}
