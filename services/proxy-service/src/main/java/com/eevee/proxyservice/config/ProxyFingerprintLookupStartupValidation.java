package com.eevee.proxyservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProxyFingerprintLookupStartupValidation implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProxyFingerprintLookupStartupValidation.class);

    private final ProxyProperties proxyProperties;

    public ProxyFingerprintLookupStartupValidation(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!proxyProperties.getFingerprintLookup().isEnabled()) {
            return;
        }
        String token = resolveToken();
        if (StringUtils.hasText(token)) {
            return;
        }
        log.warn(
                "proxy.fingerprint-lookup.internal-token is unset; POST /internal/v1/api-keys/lookup calls may fail with 403");
    }

    private String resolveToken() {
        ProxyProperties.FingerprintLookup cfg = proxyProperties.getFingerprintLookup();
        if (StringUtils.hasText(cfg.getInternalToken())) {
            return cfg.getInternalToken();
        }
        if (StringUtils.hasText(proxyProperties.getKeyService().getInternalToken())) {
            return proxyProperties.getKeyService().getInternalToken();
        }
        return proxyProperties.getTeamKeyService().getInternalToken();
    }
}
