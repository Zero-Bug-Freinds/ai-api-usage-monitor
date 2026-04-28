package com.eevee.apigateway.config;

import com.eevee.apigateway.filter.WebEdgePreAuthWebFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.eevee.apigateway.filter.ProxyTrustHeadersWebFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain internalSecurityWebFilterChain() {
        ServerHttpSecurity http = ServerHttpSecurity.http();
        http.securityMatcher(exchange -> {
            String path = exchange.getRequest().getPath().value();
            boolean matched = path.startsWith("/internal/") || path.startsWith("/actuator/");
            log.debug("[SecurityChain:internal] path={} matched={}", path, matched);
            return matched
                    ? org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.match()
                    : org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.notMatch();
        });
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
        http.authorizeExchange(ex -> ex
                .pathMatchers("/internal/web-edge/auth/resolve").permitAll()
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyExchange().denyAll()
        );
        return http.build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityWebFilterChain securityWebFilterChain(
            GatewayProperties gatewayProperties,
            ObjectProvider<ReactiveJwtDecoder> jwtDecoderProvider
    ) {
        ServerHttpSecurity http = ServerHttpSecurity.http();
        http.securityMatcher(exchange -> {
            String path = exchange.getRequest().getPath().value();
            boolean matched = path.startsWith("/api/");
            log.debug("[SecurityChain:api] path={} matched={}", path, matched);
            return matched
                    ? org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.match()
                    : org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.notMatch();
        });
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
        http.cors(cors -> {});

        if (gatewayProperties.isDevMode()) {
            http.authorizeExchange(ex -> ex
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers("/api/v1/ai/**").permitAll()
                    .pathMatchers("/api/v1/usage/**").permitAll()
                    .pathMatchers("/api/v1/expenditure/**").permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/identity/auth/signup", "/api/identity/auth/login",
                            "/api/identity/auth/forgot-password", "/api/identity/auth/reset-password").permitAll()
                    .pathMatchers("/api/identity/**").permitAll()
                    .pathMatchers("/api/team/**").permitAll()
                    .pathMatchers("/api/notification/**").permitAll()
                    .anyExchange().denyAll()
            );
        } else {
            ReactiveJwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
            if (jwtDecoder == null) {
                throw new IllegalStateException("ReactiveJwtDecoder bean is required when gateway.dev-mode=false");
            }
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)));
            http.authorizeExchange(ex -> ex
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers("/api/v1/ai/**").authenticated()
                    .pathMatchers("/api/v1/usage/**").authenticated()
                    .pathMatchers("/api/v1/expenditure/**").authenticated()
                    .pathMatchers(HttpMethod.POST, "/api/identity/auth/signup", "/api/identity/auth/login",
                            "/api/identity/auth/forgot-password", "/api/identity/auth/reset-password").permitAll()
                    .pathMatchers("/api/identity/**").authenticated()
                    .pathMatchers("/api/team/**").authenticated()
                    .pathMatchers("/api/notification/**").authenticated()
                    .anyExchange().denyAll()
            );
        }

        http.addFilterBefore(new WebEdgePreAuthWebFilter(gatewayProperties), SecurityWebFiltersOrder.AUTHENTICATION);
        http.addFilterAfter(new ProxyTrustHeadersWebFilter(gatewayProperties), SecurityWebFiltersOrder.AUTHORIZATION);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewayProperties gatewayProperties) {
        List<String> allowedOrigins = gatewayProperties.getCors().getAllowedOrigins();
        if (allowedOrigins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException(
                    "gateway.cors.allowed-origins must use explicit origins (no wildcard) when credentials are enabled");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Correlation-Id",
                "X-User-Id",
                "X-Platform-User-Id",
                "X-Team-Id",
                "X-Scope-Type"
        ));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}