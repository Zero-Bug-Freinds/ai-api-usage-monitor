package com.zerobugfreinds.identity_service.security;

import com.zerobugfreinds.identity_service.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 액세스 토큰 생성 전용 컴포넌트.
 */
@Component
public class JwtTokenProvider {

	private static final String CLAIM_USER_ID = "userId";
	private static final String CLAIM_ROLE = "role";
	private static final String CLAIM_ACTIVE_TEAM_ID = "active_team_id";
	private static final String CLAIM_TOKEN_TYPE = "tokenType";
	private static final String TOKEN_TYPE_REFRESH = "refresh";

	private final SecretKey signingKey;
	private final long accessTokenTtlSeconds;
	private final long refreshTokenTtlSeconds;

	public JwtTokenProvider(
			@Value("${security.jwt.secret}") String secret,
			@Value("${security.jwt.access-token-ttl-seconds:3600}") long accessTokenTtlSeconds,
			@Value("${security.jwt.refresh-token-ttl-seconds:1209600}") long refreshTokenTtlSeconds
	) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
	}

	/**
	 * 로그인한 사용자 정보를 subject/claim 으로 담아 액세스 토큰을 발행한다.
	 */
	public String generateAccessToken(User user) {
		return createAccessToken(user, null);
	}

	/**
	 * 팀 컨텍스트(activeTeamId)를 포함한 액세스 토큰을 발행한다.
	 */
	public String createAccessToken(User user, Long activeTeamId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);
		var builder = Jwts.builder()
				.subject(user.getEmail())
				.claim(CLAIM_USER_ID, user.getId())
				.claim(CLAIM_ROLE, user.getRole().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey);
		if (activeTeamId != null) {
			builder.claim(CLAIM_ACTIVE_TEAM_ID, activeTeamId);
		}
		return builder.compact();
	}

	public String createRefreshToken(User user, Long activeTeamId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(refreshTokenTtlSeconds);
		var builder = Jwts.builder()
				.subject(user.getEmail())
				.claim(CLAIM_USER_ID, user.getId())
				.claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey);
		if (activeTeamId != null) {
			builder.claim(CLAIM_ACTIVE_TEAM_ID, activeTeamId);
		}
		return builder.compact();
	}

	public long getAccessTokenTtlSeconds() {
		return accessTokenTtlSeconds;
	}

	public long getRefreshTokenTtlSeconds() {
		return refreshTokenTtlSeconds;
	}
}
