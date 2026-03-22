package com.eevee.proxygateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    private final Map<String, ProviderEndpoint> providers = new HashMap<>();
    private KeyService keyService = new KeyService();
    private Rabbit rabbit = new Rabbit();
    private Security security = new Security();

    public Map<String, ProviderEndpoint> getProviders() {
        return providers;
    }

    public KeyService getKeyService() {
        return keyService;
    }

    public void setKeyService(KeyService keyService) {
        this.keyService = keyService;
    }

    public Rabbit getRabbit() {
        return rabbit;
    }

    public void setRabbit(Rabbit rabbit) {
        this.rabbit = rabbit;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class ProviderEndpoint {
        private String baseUrl = "https://example.invalid";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class KeyService {
        private String baseUrl = "http://localhost:8090";
        private String internalToken = "";
        private String mockKey = "";
        private String cacheTtl = "PT5M";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getInternalToken() {
            return internalToken;
        }

        public void setInternalToken(String internalToken) {
            this.internalToken = internalToken;
        }

        public String getMockKey() {
            return mockKey;
        }

        public void setMockKey(String mockKey) {
            this.mockKey = mockKey;
        }

        public String getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(String cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    public static class Rabbit {
        private String usageExchange = "usage.events";
        private String usageRoutingKey = "usage.recorded";

        public String getUsageExchange() {
            return usageExchange;
        }

        public void setUsageExchange(String usageExchange) {
            this.usageExchange = usageExchange;
        }

        public String getUsageRoutingKey() {
            return usageRoutingKey;
        }

        public void setUsageRoutingKey(String usageRoutingKey) {
            this.usageRoutingKey = usageRoutingKey;
        }
    }

    public static class Security {
        private boolean localDevHeaders = true;
        private String jwtSecret = "";

        public boolean isLocalDevHeaders() {
            return localDevHeaders;
        }

        public void setLocalDevHeaders(boolean localDevHeaders) {
            this.localDevHeaders = localDevHeaders;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }
    }
}
