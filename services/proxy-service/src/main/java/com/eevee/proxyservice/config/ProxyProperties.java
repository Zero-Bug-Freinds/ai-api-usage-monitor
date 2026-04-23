package com.eevee.proxyservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    private final Map<String, ProviderEndpoint> providers = new HashMap<>();
    private KeyService keyService = new KeyService();
    private Rabbit rabbit = new Rabbit();
    private Gateway gateway = new Gateway();

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

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
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
        private String mockKeyOpenai = "";
        private String mockKeyGoogle = "";
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

        public String getMockKeyOpenai() {
            return mockKeyOpenai;
        }

        public void setMockKeyOpenai(String mockKeyOpenai) {
            this.mockKeyOpenai = mockKeyOpenai;
        }

        public String getMockKeyGoogle() {
            return mockKeyGoogle;
        }

        public void setMockKeyGoogle(String mockKeyGoogle) {
            this.mockKeyGoogle = mockKeyGoogle;
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

    /**
     * Trust boundary with API Gateway ({@code docs/contracts/gateway-proxy.md}).
     */
    public static class Gateway {
        /**
         * When true, {@code X-Gateway-Auth} must match {@link #sharedSecret}.
         */
        private boolean requireAuth = false;
        private String sharedSecret = "";

        public boolean isRequireAuth() {
            return requireAuth;
        }

        public void setRequireAuth(boolean requireAuth) {
            this.requireAuth = requireAuth;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret;
        }
    }
}
