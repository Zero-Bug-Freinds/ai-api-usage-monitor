package com.zerobugfreinds.identity_service.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * PostgreSQL은 {@code INSERT ... ON CONFLICT}; 통합 테스트(H2)는 {@code MERGE ... KEY}.
 */
@Repository
public class RefreshTokenRepositoryImpl implements RefreshTokenRepositoryCustom {

	private static final String POSTGRES_UPSERT = """
			INSERT INTO refresh_tokens (user_id, token_hash, active_team_id, expires_at, created_at)
			VALUES (:userId, :tokenHash, :activeTeamId, :expiresAt, :createdAt)
			ON CONFLICT (user_id) DO UPDATE SET
				token_hash = EXCLUDED.token_hash,
				active_team_id = EXCLUDED.active_team_id,
				expires_at = EXCLUDED.expires_at,
				created_at = EXCLUDED.created_at
			""";

	private static final String H2_MERGE = """
			MERGE INTO refresh_tokens (user_id, token_hash, active_team_id, expires_at, created_at) KEY (user_id)
			VALUES (:userId, :tokenHash, :activeTeamId, :expiresAt, :createdAt)
			""";

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public int upsertByUserId(
			Long userId,
			String tokenHash,
			Long activeTeamId,
			Instant expiresAt,
			Instant createdAt
	) {
		Dialect dialect = entityManager.getEntityManagerFactory()
				.unwrap(SessionFactoryImplementor.class)
				.getJdbcServices()
				.getDialect();
		String sql;
		if (dialect instanceof PostgreSQLDialect) {
			sql = POSTGRES_UPSERT;
		} else if (dialect instanceof H2Dialect) {
			sql = H2_MERGE;
		} else {
			throw new IllegalStateException(
					"Unsupported JDBC dialect for refresh_tokens upsert: " + dialect.getClass().getName()
			);
		}
		return entityManager.createNativeQuery(sql)
				.setParameter("userId", userId)
				.setParameter("tokenHash", tokenHash)
				.setParameter("activeTeamId", activeTeamId)
				.setParameter("expiresAt", expiresAt)
				.setParameter("createdAt", createdAt)
				.executeUpdate();
	}
}
