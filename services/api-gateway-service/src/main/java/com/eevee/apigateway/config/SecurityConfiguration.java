package com.eevee.apigateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.eevee.apigateway.filter.ProxyTrustHeadersWebFilter;
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

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            GatewayProperties gatewayProperties,
            @Autowired(required = false) ReactiveJwtDecoder jwtDecoder
    ) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
        http.cors(cors -> {});

        if (gatewayProperties.isDevMode()) {
            http.authorizeExchange(ex -> ex
                    .pathMatchers("/actuator/health", "/actuator/info").permitAll()
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
            if (jwtDecoder == null) {
                throw new IllegalStateException(
                        "gateway.dev-mode=false requires gateway.jwt.secret and a ReactiveJwtDecoder bean");
            }
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)));
            http.authorizeExchange(ex -> ex
                    .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                    .pathMatchers("/api/v1/ai/**").authenticated()
                    .pathMatchers("/api/v1/usage/**").authenticated()
                    .pathMatchers("/api/v1/expenditure/**").authenticated()
                    .pathMatchers(HttpMethod.POST, "/api/identity/auth/signup", "/api/identity/auth/login",
                            "/api/identity/auth/forgot-password", "/api/identity/auth/reset-password").permitAll()
                    .pathMatchers("/api/identity/**").authenticated()
                    .pathMatchers("/api/team/**").authenticated()
                    .pathMatchers("/api/notification/**").permitAll()
                    .anyExchange().denyAll()
            );
        }

        http.addFilterAfter(new ProxyTrustHeadersWebFilter(gatewayProperties), SecurityWebFiltersOrder.AUTHORIZATION);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewayProperties gatewayProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(gatewayProperties.getCors().getAllowedOrigins());
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