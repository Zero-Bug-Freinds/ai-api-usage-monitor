package com.zerobugfreinds.identity_service.dto;

import com.zerobugfreinds.identity_service.entity.Role;

/**
 * 프로필 갱신 후 클라이언트에 돌려줄 사용자 요약 (비밀번호 제외).
 */
public record ProfileUpdateResponse(Long userId, String email, String name, Role role) {
}
