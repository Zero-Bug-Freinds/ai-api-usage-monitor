package com.eevee.billingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.identity")
public class IdentityProperties {

    /**
     * When false or base-url is blank, budget lookups are skipped (expenditure-only mode).
     */
    private boolean enabled;

    /**
     * Example: {@code http://localhost:8090}
     */
    private String baseUrl = "";

    /**
     * Path appended to base-url, with literal {@code {userId}} for substitution.
     * Example: {@code /api/v1/internal/users/{userId}/monthly-budget-usd}
     */
    private String budgetPathTemplate = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBudgetPathTemplate() {
        return budgetPathTemplate;
    }

    public void setBudgetPathTemplate(String budgetPathTemplate) {
        this.budgetPathTemplate = budgetPathTemplate;
    }
}
