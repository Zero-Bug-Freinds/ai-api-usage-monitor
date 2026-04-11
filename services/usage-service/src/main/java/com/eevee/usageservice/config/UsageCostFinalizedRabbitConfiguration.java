package com.eevee.usageservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares {@code billing.events} / {@code usage.cost.finalized} consumer queue for
 * {@link com.eevee.usage.events.UsageCostFinalizedEvent} (separate from {@code usage.recorded}).
 */
@Configuration
@ConditionalOnProperty(prefix = "usage.rabbit.cost-finalized", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsageCostFinalizedRabbitConfiguration {

    @Bean
    public TopicExchange usageCostFinalizedExchange(UsageRabbitProperties props) {
        return new TopicExchange(props.getCostFinalized().getExchange(), true, false);
    }

    @Bean
    public Queue usageCostFinalizedQueue(UsageRabbitProperties props) {
        return new Queue(props.getCostFinalized().getQueue(), true);
    }

    @Bean
    public Binding usageCostFinalizedBinding(
            Queue usageCostFinalizedQueue,
            TopicExchange usageCostFinalizedExchange,
            UsageRabbitProperties props
    ) {
        return BindingBuilder.bind(usageCostFinalizedQueue)
                .to(usageCostFinalizedExchange)
                .with(props.getCostFinalized().getRoutingKey());
    }
}
