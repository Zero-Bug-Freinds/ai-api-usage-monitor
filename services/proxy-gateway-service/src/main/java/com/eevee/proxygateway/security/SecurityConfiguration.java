package com.eevee.proxygateway.security;

import com.eevee.proxygateway.config.ProxyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            @Autowired(required = false) ReactiveJwtDecoder jwtDecoder
    ) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);

        if (jwtDecoder != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
            }));
        }

        http.authorizeExchange(ex -> ex
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers("/proxy/**").authenticated()
                .anyExchange().denyAll()
        );

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "proxy.security", name = "jwt-secret", matchIfMissing = false)
    public ReactiveJwtDecoder reactiveJwtDecoder(ProxyProperties proxyProperties) {
        String secret = proxyProperties.getSecurity().getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("proxy.security.jwt-secret must be non-empty when set");
        }
        return NimbusReactiveJwtDecoder.withSecretKey(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")
        ).build();
    }
}
