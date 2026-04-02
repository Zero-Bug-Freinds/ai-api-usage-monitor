package com.eevee.usageservice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UsageStartupValidation implements ApplicationRunner {

    private final UsageServiceProperties usageServiceProperties;

    public UsageStartupValidation(UsageServiceProperties usageServiceProperties) {
        this.usageServiceProperties = usageServiceProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String sharedSecret = usageServiceProperties.getGateway().getSharedSecret();
        if (!StringUtils.hasText(sharedSecret)) {
            throw new IllegalStateException(
                    "usage.gateway.shared-secret is required. Set GATEWAY_SHARED_SECRET.");
        }
    }
}
