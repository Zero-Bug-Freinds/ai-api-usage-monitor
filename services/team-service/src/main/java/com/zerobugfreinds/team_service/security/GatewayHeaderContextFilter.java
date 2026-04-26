package com.zerobugfreinds.team_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 게이트웨이에서 주입한 사용자/팀 헤더를 읽어 요청 컨텍스트를 구성한다.
 */
@Component
public class GatewayHeaderContextFilter extends OncePerRequestFilter {

	public static final String USER_ID_HEADER = "X-User-Id";
	public static final String TEAM_ID_HEADER = "X-Team-Id";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		try {
			String userId = request.getHeader(USER_ID_HEADER);
			if (StringUtils.hasText(userId)) {
				String normalizedUserId = userId.trim();
				TeamContextHolder.setUserId(normalizedUserId);
				UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(
								normalizedUserId,
								null,
								List.of(new SimpleGrantedAuthority("ROLE_USER"))
						);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}

			String teamIdHeader = request.getHeader(TEAM_ID_HEADER);
			if (StringUtils.hasText(teamIdHeader)) {
				TeamContextHolder.setTeamId(Long.parseLong(teamIdHeader.trim()));
			}

			filterChain.doFilter(request, response);
		} finally {
			TeamContextHolder.clear();
		}
	}
}
