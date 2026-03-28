package com.zerobugfreinds.identity_service.dto;

/**
 * 세션(인증 상태) 확인 응답 본문.
 */
public record SessionResponse(String email, String role, boolean authenticated) {
}
