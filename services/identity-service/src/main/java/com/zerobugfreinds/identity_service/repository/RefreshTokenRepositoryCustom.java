package com.zerobugfreinds.identity_service.repository;

import java.time.Instant;

/**
 * 리프레시 토큰 단일 구문 저장(UPSERT / MERGE) — DB 방언별 구현은 {@link RefreshTokenRepositoryImpl} 참고.
 * 반환값은 이번 호출이 <strong>신규 행 삽입</strong>이었는지(첫 리프레시 토큰) 여부이다.
 */
public interface RefreshTokenRepositoryCustom {

	/**
	 * 사용자당 한 행만 유지한다. 갱신 시 {@code created_at} 은 최초 발급 시각을 유지한다.
	 *
	 * @return {@code created_at} 이 이번에 넘긴 {@code createdAt} 과 동일(동일 DB 정밀도)이면 {@code true} (신규 삽입)
	 */
	boolean upsertByUserId(
			Long userId,
			String tokenHash,
			Long activeTeamId,
			Instant expiresAt,
			Instant createdAt
	);
}
