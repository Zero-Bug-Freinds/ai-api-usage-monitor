package com.eevee.usageservice.security;

import com.eevee.usageservice.config.UsageServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Trusts {@code X-User-Id} (and optional {@code X-Gateway-Auth}) for requests behind API Gateway.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UsageGatewayTrustFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "usage.authenticatedUserId";

    private static final String HDR_USER = "X-User-Id";
    private static final String HDR_GATEWAY_AUTH = "X-Gateway-Auth";

    private final UsageServiceProperties properties;

    public UsageGatewayTrustFilter(UsageServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/usage");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String expected = properties.getGateway().getSharedSecret();
        if (StringUtils.hasText(expected)) {
            String actual = request.getHeader(HDR_GATEWAY_AUTH);
            if (!expected.equals(actual)) {
                response.sendError(HttpStatus.FORBIDDEN.value());
                return;
            }
        }

        String userId = request.getHeader(HDR_USER);
        if (!StringUtils.hasText(userId)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing X-User-Id");
            return;
        }
        request.setAttribute(ATTR_USER_ID, userId.trim());
        filterChain.doFilter(request, response);
    }
}