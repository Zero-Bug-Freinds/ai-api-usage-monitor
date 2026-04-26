package com.zerobugfreinds.identity_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 게이트웨이가 주입한 사용자 헤더(X-User-Id)를 신뢰해 SecurityContext 를 구성한다.
 */
@Component
public class GatewayHeaderInterceptor extends OncePerRequestFilter {

	public static final String USER_ID_HEADER = "X-User-Id";
	private static final Logger log = LoggerFactory.getLogger(GatewayHeaderInterceptor.class);

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String userIdHeader = request.getHeader(USER_ID_HEADER);
		if (userIdHeader != null && !userIdHeader.isBlank()) {
			try {
				Long userId = Long.parseLong(userIdHeader.trim());
				UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(
								userId.toString(),
								null,
								List.of(new SimpleGrantedAuthority("ROLE_USER"))
						);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (NumberFormatException ex) {
				SecurityContextHolder.clearContext();
				log.debug("X-User-Id 헤더 값이 유효하지 않습니다: {}", userIdHeader);
			}
		}

		filterChain.doFilter(request, response);
	}
}
