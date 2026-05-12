package com.zerobugfreinds.identity_service.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * PostgreSQL은 {@code INSERT ... ON CONFLICT}; 통합 테스트(H2)는 {@code MERGE} 로 동일 의미(충돌 시 {@code created_at} 유지).
 */
@Repository
public class RefreshTokenRepositoryImpl implements RefreshTokenRepositoryCustom {

	private static final String POSTGRES_UPSERT = """
			INSERT INTO refresh_tokens (user_id, token_hash, active_team_id, expires_at, created_at)
			VALUES (:userId, :tokenHash, :activeTeamId, :expiresAt, :createdAt)
			ON CONFLICT (user_id) DO UPDATE SET
				token_hash = EXCLUDED.token_hash,
				active_team_id = EXCLUDED.active_team_id,
				expires_at = EXCLUDED.expires_at
			""";

	private static final String H2_MERGE = """
			MERGE INTO refresh_tokens AS rt
			USING (
				SELECT CAST(:userId AS BIGINT) AS user_id,
					CAST(:tokenHash AS VARCHAR(64)) AS token_hash,
					CAST(:activeTeamId AS BIGINT) AS active_team_id,
					CAST(:expiresAt AS TIMESTAMP WITH TIME ZONE) AS expires_at,
					CAST(:createdAt AS TIMESTAMP WITH TIME ZONE) AS created_at
			) AS v
			ON rt.user_id = v.user_id
			WHEN MATCHED THEN UPDATE SET
				rt.token_hash = v.token_hash,
				rt.active_team_id = v.active_team_id,
				rt.expires_at = v.expires_at
			WHEN NOT MATCHED THEN INSERT (user_id, token_hash, active_team_id, expires_at, created_at)
				VALUES (v.user_id, v.token_hash, v.active_team_id, v.expires_at, v.created_at)
			""";

	private static final String SELECT_CREATED_AT_BY_USER = """
			SELECT created_at FROM refresh_tokens WHERE user_id = :userId
			""";

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public boolean upsertByUserId(
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
		String upsertSql;
		if (dialect instanceof PostgreSQLDialect) {
			upsertSql = POSTGRES_UPSERT;
		} else if (dialect instanceof H2Dialect) {
			upsertSql = H2_MERGE;
		} else {
			throw new IllegalStateException(
					"Unsupported JDBC dialect for refresh_tokens upsert: " + dialect.getClass().getName()
			);
		}
		entityManager.createNativeQuery(upsertSql)
				.setParameter("userId", userId)
				.setParameter("tokenHash", tokenHash)
				.setParameter("activeTeamId", activeTeamId)
				.setParameter("expiresAt", expiresAt)
				.setParameter("createdAt", createdAt)
				.executeUpdate();

		Object raw = entityManager.createNativeQuery(SELECT_CREATED_AT_BY_USER)
				.setParameter("userId", userId)
				.getSingleResult();
		Instant storedCreatedAt = toInstant(raw);
		return sameInstantDbPrecision(storedCreatedAt, createdAt);
	}

	private static Instant toInstant(Object raw) {
		if (raw instanceof Instant instant) {
			return instant;
		}
		if (raw instanceof Timestamp timestamp) {
			return timestamp.toInstant();
		}
		if (raw instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime.toInstant();
		}
		throw new IllegalStateException(
				"Unsupported created_at JDBC type: " + (raw == null ? "null" : raw.getClass().getName())
		);
	}

	/**
	 * JDBC/DB 가 마이크로·밀리초 단위로 잘라 저장할 수 있어, 동일 시각 비교는 DB 정밀도에 맞춘다.
	 */
	private static boolean sameInstantDbPrecision(Instant stored, Instant requested) {
		return stored.truncatedTo(ChronoUnit.MILLIS).equals(requested.truncatedTo(ChronoUnit.MILLIS));
	}
}
