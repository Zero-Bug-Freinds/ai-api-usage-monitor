package com.zerobugfreinds.identity_service.dto;

import com.zerobugfreinds.identity_service.entity.Role;

/**
 * 회원가입 성공 시 클라이언트에 반환할 사용자 요약 정보 (비밀번호 제외).
 */
public record SignupResponse(Long userId, String email, String name, Role role) {
}
