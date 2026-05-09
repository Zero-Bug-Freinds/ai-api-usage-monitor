package com.eevee.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * See {@code docs/contracts/gateway-proxy.md}.
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /**
     * When true, JWT is not required; clients send {@code X-User-Id} (local / staging only).
     */
    private boolean devMode = true;

    /**
     * Same value as Proxy {@code proxy.gateway.shared-secret} ({@code GATEWAY_SHARED_SECRET}).
     */
    private String sharedSecret = "";

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final ExtAi extAi = new ExtAi();

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public ExtAi getExtAi() {
        return extAi;
    }

    public static class Jwt {
        /**
         * HMAC256 secret for validating platform JWTs (same convention as legacy combined gateway module).
         */
        private String secret = "";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:8888");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class ExtAi {
        private boolean enabled = false;
        private String keyId = "";
        private String hmacSecret = "";
        private long timestampSkewSeconds = 300;
        private long nonceTtlSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }

        public long getTimestampSkewSeconds() {
            return timestampSkewSeconds;
        }

        public void setTimestampSkewSeconds(long timestampSkewSeconds) {
            this.timestampSkewSeconds = timestampSkewSeconds;
        }

        public long getNonceTtlSeconds() {
            return nonceTtlSeconds;
        }

        public void setNonceTtlSeconds(long nonceTtlSeconds) {
            this.nonceTtlSeconds = nonceTtlSeconds;
        }
    }
}
