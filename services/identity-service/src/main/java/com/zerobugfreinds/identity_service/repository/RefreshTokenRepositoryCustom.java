package com.zerobugfreinds.identity_service.repository;

import java.time.Instant;

/**
 * 리프레시 토큰 단일 구문 저장(UPSERT / MERGE) — DB 방언별 구현은 {@link RefreshTokenRepositoryImpl} 참고.
 */
public interface RefreshTokenRepositoryCustom {

	int upsertByUserId(
			Long userId,
			String tokenHash,
			Long activeTeamId,
			Instant expiresAt,
			Instant createdAt
	);
}
