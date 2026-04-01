package com.eevee.apigateway.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GatewayStartupValidation implements ApplicationRunner {

    private static final int HMAC256_MIN_SECRET_LENGTH = 32;

    private final GatewayProperties gatewayProperties;

    public GatewayStartupValidation(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateSharedSecret();
        validateJwtSecretForProdMode();
    }

    private void validateSharedSecret() {
        String sharedSecret = gatewayProperties.getSharedSecret();
        if (!StringUtils.hasText(sharedSecret)) {
            throw new IllegalStateException(
                    "gateway.shared-secret is required. Set GATEWAY_SHARED_SECRET.");
        }
    }

    private void validateJwtSecretForProdMode() {
        if (gatewayProperties.isDevMode()) {
            return;
        }

        String jwtSecret = gatewayProperties.getJwt().getSecret();
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException(
                    "gateway.dev-mode=false requires gateway.jwt.secret. Set GATEWAY_JWT_SECRET.");
        }
        if (jwtSecret.length() < HMAC256_MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "gateway.jwt.secret must be at least 32 characters for HS256.");
        }
    }
}
