package com.eevee.apigateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtDecoderConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gateway.dev-mode", havingValue = "false")
    public ReactiveJwtDecoder reactiveJwtDecoder(GatewayProperties gatewayProperties) {
        String secret = gatewayProperties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("gateway.jwt.secret is required when gateway.dev-mode=false");
        }
        return NimbusReactiveJwtDecoder.withSecretKey(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")
        ).build();
    }
}
