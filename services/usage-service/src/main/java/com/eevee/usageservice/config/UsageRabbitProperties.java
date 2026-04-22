package com.eevee.usageservice.config;

import com.eevee.usage.events.UsageCostEventAmqp;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "usage.rabbit")
public class UsageRabbitProperties {

    /**
     * Must match {@code proxy.rabbit.usage-exchange} in proxy-service.
     */
    private String exchange = "usage.events";

    /**
     * Must match {@code proxy.rabbit.usage-routing-key} in proxy-service.
     */
    private String routingKey = "usage.recorded";

    /**
     * Dedicated queue for this service (pattern A — fan-out, independent DB).
     */
    private String queue = "usage-service.queue";

    /**
     * Inbound stream from billing-service: {@link com.eevee.usage.events.UsageCostFinalizedEvent} on
     * {@link UsageCostEventAmqp#TOPIC_EXCHANGE_NAME} / {@link UsageCostEventAmqp#ROUTING_KEY_COST_FINALIZED}.
     */
    private IdentityApiKey identityApiKey = new IdentityApiKey();
    private TeamApiKey teamApiKey = new TeamApiKey();

    private CostFinalized costFinalized = new CostFinalized();

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

    public CostFinalized getCostFinalized() {
        return costFinalized;
    }

    public IdentityApiKey getIdentityApiKey() {
        return identityApiKey;
    }

    public void setIdentityApiKey(IdentityApiKey identityApiKey) {
        if (identityApiKey != null) {
            this.identityApiKey = identityApiKey;
        }
    }

    public TeamApiKey getTeamApiKey() {
        return teamApiKey;
    }

    public void setTeamApiKey(TeamApiKey teamApiKey) {
        if (teamApiKey != null) {
            this.teamApiKey = teamApiKey;
        }
    }

    public void setCostFinalized(CostFinalized costFinalized) {
        if (costFinalized != null) {
            this.costFinalized = costFinalized;
        }
    }

    public static class IdentityApiKey {
        private boolean enabled = true;
        private String exchange = "identity.events";
        private String routingKey = "identity.external-api-key.status-changed";
        private String queue = "usage-service.identity.external-api-key.queue";

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

    public static class CostFinalized {

        private boolean enabled = true;
        private String exchange = UsageCostEventAmqp.TOPIC_EXCHANGE_NAME;
        private String routingKey = UsageCostEventAmqp.ROUTING_KEY_COST_FINALIZED;
        private String queue = UsageCostEventAmqp.SUGGESTED_USAGE_SERVICE_QUEUE;

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

    public static class TeamApiKey {
        private boolean enabled = true;
        private String exchange = "team.events";
        private String routingKey = "team-member-added";
        private String queue = "usage-service.team.api-key.queue";

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
