package com.eevee.billingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    private final Gateway gateway = new Gateway();
    private final Analytics analytics = new Analytics();
    private final ApiErrorSettings error = new ApiErrorSettings();

    public Gateway getGateway() {
        return gateway;
    }

    public Analytics getAnalytics() {
        return analytics;
    }

    public ApiErrorSettings getError() {
        return error;
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
     * Response shaping for API error bodies (not security-sensitive; disable hints in locked-down prod if desired).
     */
    public static class ApiErrorSettings {
        /**
         * When true, database failure responses include a short remediation hint (local/dev friendly).
         */
        private boolean exposeDatasourceFailureHint = true;

        public boolean isExposeDatasourceFailureHint() {
            return exposeDatasourceFailureHint;
        }

        public void setExposeDatasourceFailureHint(boolean exposeDatasourceFailureHint) {
            this.exposeDatasourceFailureHint = exposeDatasourceFailureHint;
        }
    }
}
