package com.eevee.proxyservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Proxy does not validate platform JWT; trust boundary is Gateway ({@code X-Gateway-Auth} + headers).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);

        http.authorizeExchange(ex -> ex
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers("/proxy/**").authenticated()
                .anyExchange().denyAll()
        );

        return http.build();
    }
}
