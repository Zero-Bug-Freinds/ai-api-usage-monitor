package com.eevee.usageservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "usage")
public class UsageServiceProperties {

    private final Analytics analytics = new Analytics();
    private final Gateway gateway = new Gateway();

    public Analytics getAnalytics() {
        return analytics;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public static class Analytics {
        private int maxRangeDays = 400;

        public int getMaxRangeDays() {
            return maxRangeDays;
        }

        public void setMaxRangeDays(int maxRangeDays) {
            this.maxRangeDays = maxRangeDays;
        }
    }

    public static class Gateway {
        private String sharedSecret = "";

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret;
        }
    }
}