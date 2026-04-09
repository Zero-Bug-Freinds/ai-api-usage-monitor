package com.zerobugfreinds.team_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtTokenProvider {
	private final SecretKey signingKey;

	public JwtTokenProvider(@Value("${security.jwt.secret}") String secret) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

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
}
