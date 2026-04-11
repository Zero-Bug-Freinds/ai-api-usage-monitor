package com.eevee.billingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    private final Gateway gateway = new Gateway();
    private final Analytics analytics = new Analytics();

    public Gateway getGateway() {
        return gateway;
    }

    public Analytics getAnalytics() {
        return analytics;
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
