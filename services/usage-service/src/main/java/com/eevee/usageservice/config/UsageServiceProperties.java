package com.eevee.usageservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "usage")
public class UsageServiceProperties {

    private final Analytics analytics = new Analytics();
    private final Gateway gateway = new Gateway();
    private final Team team = new Team();

    public Analytics getAnalytics() {
        return analytics;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public Team getTeam() {
        return team;
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

    public static class Team {
        private String baseUrl = "http://team-service:8093";
        private int teamListCacheTtlSeconds = 300;
        private int memberCacheTtlSeconds = 300;
        private int timeoutMs = 1500;
        /**
         * When true, empty team lists from team-service are cached for TTL (legacy behavior).
         * Default false: empty lists are not cached so a later successful lookup is not suppressed.
         */
        private boolean cacheEmptyTeamList = false;
        /**
         * When true, emit masked diagnostic logs at INFO for BFF team-list path; otherwise DEBUG only.
         */
        private boolean diagnosticsLogging = false;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTeamListCacheTtlSeconds() {
            return teamListCacheTtlSeconds;
        }

        public void setTeamListCacheTtlSeconds(int teamListCacheTtlSeconds) {
            this.teamListCacheTtlSeconds = teamListCacheTtlSeconds;
        }

        public int getMemberCacheTtlSeconds() {
            return memberCacheTtlSeconds;
        }

        public void setMemberCacheTtlSeconds(int memberCacheTtlSeconds) {
            this.memberCacheTtlSeconds = memberCacheTtlSeconds;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public boolean isCacheEmptyTeamList() {
            return cacheEmptyTeamList;
        }

        public void setCacheEmptyTeamList(boolean cacheEmptyTeamList) {
            this.cacheEmptyTeamList = cacheEmptyTeamList;
        }

        public boolean isDiagnosticsLogging() {
            return diagnosticsLogging;
        }

        public void setDiagnosticsLogging(boolean diagnosticsLogging) {
            this.diagnosticsLogging = diagnosticsLogging;
        }
    }
}