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

	private final SecretKey signingKey;
	private final long accessTokenTtlSeconds;

	public JwtTokenProvider(
			@Value("${security.jwt.secret}") String secret,
			@Value("${security.jwt.access-token-ttl-seconds:3600}") long accessTokenTtlSeconds
	) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	/**
	 * 로그인한 사용자 정보를 subject/claim 으로 담아 액세스 토큰을 발행한다.
	 */
	public String generateAccessToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

		return Jwts.builder()
				.subject(user.getEmail())
				.claim("userId", user.getId())
				.claim("role", user.getRole().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey)
				.compact();
	}

	public long getAccessTokenTtlSeconds() {
		return accessTokenTtlSeconds;
	}
}
