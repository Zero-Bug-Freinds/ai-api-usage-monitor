package com.eevee.billingservice.config;

import com.eevee.usage.events.UsageCostEventAmqp;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.rabbit")
public class BillingRabbitProperties {

    private String exchange = "usage.events";
    private String routingKey = "usage.recorded";
    private String queue = "billing-service.queue";

    /**
     * Outbound billing → usage (and similar) cost-finalized events ({@code billing.events} / {@code usage.cost.finalized}).
     */
    private final CostOut costOut = new CostOut();

    /**
     * Outbound billing → notification (and similar) budget-threshold events.
     */
    private final BudgetOut budgetOut = new BudgetOut();

    /**
     * Outbound billing → notification team budget-threshold events (team registered keys only).
     */
    private final TeamBudgetOut teamBudgetOut = new TeamBudgetOut();

    /**
     * Inbound cost correction commands (delta adjustments).
     */
    private final CorrectionIn correctionIn = new CorrectionIn();

    /**
     * Inbound team-service Team API key status events (read model sync).
     */
    private final TeamApiKeyIn teamApiKeyIn = new TeamApiKeyIn();

    /**
     * Outbound billing → usage/analytics (and similar) after a correction is applied.
     */
    private final CorrectionOut correctionOut = new CorrectionOut();

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public CostOut getCostOut() {
        return costOut;
    }

    public BudgetOut getBudgetOut() {
        return budgetOut;
    }

    public TeamBudgetOut getTeamBudgetOut() {
        return teamBudgetOut;
    }

    public CorrectionIn getCorrectionIn() {
        return correctionIn;
    }

    public CorrectionOut getCorrectionOut() {
        return correctionOut;
    }

    public TeamApiKeyIn getTeamApiKeyIn() {
        return teamApiKeyIn;
    }

    public static class CostOut {

        private boolean enabled = true;
        private String exchange = UsageCostEventAmqp.TOPIC_EXCHANGE_NAME;
        private String routingKey = UsageCostEventAmqp.ROUTING_KEY_COST_FINALIZED;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }
    }

    public static class BudgetOut {

        private boolean enabled = true;
        private String exchange = "billing.events";
        private String routingKey = "billing.budget.threshold.reached";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }
    }

    public static class TeamBudgetOut {

        private boolean enabled = true;
        private String exchange = "billing.events";
        private String routingKey = "billing.team.budget.threshold.reached";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }
    }

    public static class CorrectionIn {

        private boolean enabled = true;
        private String exchange = "billing.events";
        private String routingKey = "billing.cost.correct";
        private String queue = "billing-service.cost-correction.queue";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }

        public String getQueue() {
            return queue;
        }

        public void setQueue(String queue) {
            this.queue = queue;
        }
    }

    public static class CorrectionOut {

        private boolean enabled = true;
        private String exchange = "billing.events";
        private String routingKey = "billing.cost.corrected";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }
    }

    public static class TeamApiKeyIn {

        private boolean enabled = true;
        private String exchange = "team.api-key.exchange";
        private String routingKey = "team.api-key.status.changed";
        private String queue = "billing-service.team-api-key.status.changed.queue";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }

        public String getQueue() {
            return queue;
        }

        public void setQueue(String queue) {
            this.queue = queue;
        }
    }
}
