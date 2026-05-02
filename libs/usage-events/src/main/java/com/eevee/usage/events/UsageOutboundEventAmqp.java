package com.eevee.usage.events;

/**
 * RabbitMQ names for <strong>usage-service outbound</strong> analytics / prediction signals.
 * These messages are published by {@code usage-service} onto its usage topic exchange;
 * subscribers (e.g. {@code agent-service}) bind their own queues.
 */
public final class UsageOutboundEventAmqp {

    /**
     * Topic exchange shared with other usage streams (e.g. {@code usage.recorded},
     * {@code usage.summary.aggregate}). Publishers route by {@link #ROUTING_KEY_PREDICTION_SIGNALS}.
     */
    public static final String TOPIC_EXCHANGE_NAME = "usage.events";

    /**
     * Routing key for {@link UsagePredictionSignalsEvent} payloads.
     */
    public static final String ROUTING_KEY_PREDICTION_SIGNALS = "usage.prediction.signals";

    /**
     * Suggested durable queue for {@code agent-service}; bind to {@link #TOPIC_EXCHANGE_NAME}
     * with {@link #ROUTING_KEY_PREDICTION_SIGNALS}.
     */
    public static final String SUGGESTED_AGENT_SERVICE_QUEUE = "agent-service.usage-prediction-signals.queue";

    private UsageOutboundEventAmqp() {
    }
}
