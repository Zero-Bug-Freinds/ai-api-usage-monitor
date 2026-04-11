package com.eevee.usage.events;

/**
 * RabbitMQ names for the <strong>billing → usage (and similar)</strong> cost-finalization stream.
 * This is intentionally separate from the inbound proxy/billing consumption path:
 * <ul>
 *   <li><strong>Inbound (unchanged)</strong>: topic exchange {@code usage.events}, routing key
 *       {@code usage.recorded}, queue e.g. {@code billing-service.queue} — see billing
 *       {@code billing.rabbit.*}.</li>
 *   <li><strong>Outbound cost events (this class)</strong>: dedicated topic exchange and routing key
 *       so consumers do not bind the same queue to {@code usage.recorded} (avoids redelivery loops
 *       and mistaken re-processing of {@link UsageRecordedEvent} as {@link UsageCostFinalizedEvent}).</li>
 * </ul>
 *
 * <p><strong>Deployment</strong>: declare the exchange as durable topic; bind the usage-service queue
 * with routing key {@link #ROUTING_KEY_COST_FINALIZED} (exact match). Publishers use
 * {@link #TOPIC_EXCHANGE_NAME} + {@link #ROUTING_KEY_COST_FINALIZED}.
 */
public final class UsageCostEventAmqp {

    /**
     * Topic exchange for messages typed {@link UsageCostFinalizedEvent}.
     * Not {@code usage.events} — separates billing emission from the proxy usage stream.
     */
    public static final String TOPIC_EXCHANGE_NAME = "billing.events";

    /**
     * Routing key for {@link UsageCostFinalizedEvent} (all schema versions published under this key
     * use {@link UsageCostFinalizedEvent#schemaVersion()} for payload-level versioning).
     */
    public static final String ROUTING_KEY_COST_FINALIZED = "usage.cost.finalized";

    /**
     * Suggested durable queue for usage-service; bind to {@link #TOPIC_EXCHANGE_NAME} with
     * {@link #ROUTING_KEY_COST_FINALIZED}. Other consumers should use their own queue names.
     */
    public static final String SUGGESTED_USAGE_SERVICE_QUEUE = "usage-service.usage-cost-finalized.queue";

    private UsageCostEventAmqp() {
    }
}
