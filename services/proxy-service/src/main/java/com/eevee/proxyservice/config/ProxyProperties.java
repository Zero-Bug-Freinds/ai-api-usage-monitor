package com.eevee.proxyservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    private final Map<String, ProviderEndpoint> providers = new HashMap<>();
    private KeyService keyService = new KeyService();
    private TeamKeyService teamKeyService = new TeamKeyService();
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

    public TeamKeyService getTeamKeyService() {
        return teamKeyService;
    }

    public void setTeamKeyService(TeamKeyService teamKeyService) {
        this.teamKeyService = teamKeyService;
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
        private List<ReverseLookupMock> reverseLookupMocks = new ArrayList<>();

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

        public List<ReverseLookupMock> getReverseLookupMocks() {
            return reverseLookupMocks;
        }

        public void setReverseLookupMocks(List<ReverseLookupMock> reverseLookupMocks) {
            this.reverseLookupMocks = reverseLookupMocks;
        }
    }

    public static class ReverseLookupMock {
        private String rawKey = "";
        private String rawKeySha256 = "";
        private String provider = "";
        private String keyId = "";
        private String userId = "";
        private String teamId = "";
        private String alias = "";
        private String status = "ACTIVE";
        private String keySource = "reverse_lookup";

        public String getRawKey() {
            return rawKey;
        }

        public void setRawKey(String rawKey) {
            this.rawKey = rawKey;
        }

        public String getRawKeySha256() {
            return rawKeySha256;
        }

        public void setRawKeySha256(String rawKeySha256) {
            this.rawKeySha256 = rawKeySha256;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getTeamId() {
            return teamId;
        }

        public void setTeamId(String teamId) {
            this.teamId = teamId;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getKeySource() {
            return keySource;
        }

        public void setKeySource(String keySource) {
            this.keySource = keySource;
        }
    }

    public static class TeamKeyService {
        private String baseUrl = "http://localhost:8093";
        private String internalToken = "";
        private String pathTemplate = "/internal/api-keys/{provider}";

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

        public String getPathTemplate() {
            return pathTemplate;
        }

        public void setPathTemplate(String pathTemplate) {
            this.pathTemplate = pathTemplate;
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
