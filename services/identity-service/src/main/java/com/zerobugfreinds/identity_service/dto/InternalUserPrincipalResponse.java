package com.zerobugfreinds.identity_service.dto;

/**
 * team-service 등 내부 호출자가 사용자를 숫자 id·이메일 어느 쪽으로 넘겨도 동일인으로 매칭할 수 있도록 한다.
 */
public record InternalUserPrincipalResponse(String userId, String email) {
}
