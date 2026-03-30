package com.zerobugfreinds.identity_service.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Authorization Bearer 토큰을 읽어 SecurityContext 를 구성한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
			String token = authorization.substring(BEARER_PREFIX.length());

			try {
				Claims claims = jwtTokenProvider.validateAndGetClaims(token);
				Long userId = claimToLong(claims.get("userId"));
				String email = claims.getSubject();
				String role = claims.get("role", String.class);

				if (userId == null || email == null || role == null) {
					SecurityContextHolder.clearContext();
					log.debug("JWT 클레임이 불완전하여 인증을 설정하지 않습니다");
					filterChain.doFilter(request, response);
					return;
				}

				IdentityUserPrincipal principal = new IdentityUserPrincipal(userId, email, role);
				UsernamePasswordAuthenticationToken authenticationToken =
						new UsernamePasswordAuthenticationToken(
								principal,
								null,
								List.of(new SimpleGrantedAuthority("ROLE_" + role))
						);
				authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authenticationToken);
			} catch (IllegalArgumentException ex) {
				// 잘못된 토큰은 인증을 세우지 않고 익명 요청으로 처리한다.
				SecurityContextHolder.clearContext();
				log.debug("유효하지 않은 JWT 토큰 요청: {}", ex.getMessage());
			}
		}

		filterChain.doFilter(request, response);
	}

	private static Long claimToLong(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(value.toString());
	}
}
