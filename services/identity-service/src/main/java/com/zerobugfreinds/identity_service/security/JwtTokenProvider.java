package com.zerobugfreinds.identity_service.security;

import com.zerobugfreinds.identity_service.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
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

	/**
	 * JWT 서명/만료를 검증하고 payload(claim) 를 반환한다.
	 */
	public Claims validateAndGetClaims(String token) {
		try {
			Jws<Claims> jws = Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token);
			return jws.getPayload();
		} catch (JwtException | IllegalArgumentException ex) {
			throw new IllegalArgumentException("유효하지 않은 토큰입니다", ex);
		}
	}

	public long getAccessTokenTtlSeconds() {
		return accessTokenTtlSeconds;
	}
}
