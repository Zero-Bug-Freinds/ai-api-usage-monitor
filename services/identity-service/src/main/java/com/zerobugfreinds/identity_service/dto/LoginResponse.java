package com.zerobugfreinds.identity_service.dto;

/**
 * 로그인 성공 응답 본문.
 */
public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
