package com.eevee.usageservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "usage")
public class UsageServiceProperties {

    private final Analytics analytics = new Analytics();
    private final Gateway gateway = new Gateway();
    private final Reporting reporting = new Reporting();

    public Analytics getAnalytics() {
        return analytics;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public Reporting getReporting() {
        return reporting;
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

    /**
     * Dashboard/analytics: calendar days and month buckets for {@code occurred_at} use this IANA zone.
     */
    public static class Reporting {
        private String timeZone = "Asia/Seoul";

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
    }
}