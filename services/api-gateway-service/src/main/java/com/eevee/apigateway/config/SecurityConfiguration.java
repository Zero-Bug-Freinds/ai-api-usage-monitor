package com.eevee.apigateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.eevee.apigateway.filter.ProxyTrustHeadersWebFilter;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

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

        if (gatewayProperties.isDevMode()) {
            http.authorizeExchange(ex -> ex
                    .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                    .pathMatchers("/api/v1/ai/**").permitAll()
                    .pathMatchers("/api/v1/usage/**").permitAll()
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
                    .anyExchange().denyAll()
            );
        }

        http.addFilterAfter(new ProxyTrustHeadersWebFilter(gatewayProperties), SecurityWebFiltersOrder.AUTHORIZATION);
        return http.build();
    }
}