package com.eevee.usageservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares usage and cost queues on the topic exchange used for usage-domain events
 * ({@code UsageRecordedEvent} from proxy, {@code UsageCostFinalizedEvent} from billing).
 */
@Configuration
public class UsageRabbitConfiguration {

    @Bean
    public TopicExchange usageExchange(UsageRabbitProperties usageRabbitProperties) {
        return new TopicExchange(usageRabbitProperties.getExchange(), true, false);
    }

    @Bean
    public Queue usageQueue(UsageRabbitProperties usageRabbitProperties) {
        return new Queue(usageRabbitProperties.getQueue(), true);
    }

    @Bean
    public Queue usageCostQueue(UsageRabbitProperties usageRabbitProperties) {
        return new Queue(usageRabbitProperties.getCostQueue(), true);
    }

    @Bean
    public Binding usageBinding(
            Queue usageQueue,
            TopicExchange usageExchange,
            UsageRabbitProperties usageRabbitProperties
    ) {
        return BindingBuilder.bind(usageQueue)
                .to(usageExchange)
                .with(usageRabbitProperties.getRoutingKey());
    }

    @Bean
    public Binding usageCostBinding(
            Queue usageCostQueue,
            TopicExchange usageExchange,
            UsageRabbitProperties usageRabbitProperties
    ) {
        return BindingBuilder.bind(usageCostQueue)
                .to(usageExchange)
                .with(usageRabbitProperties.getCostRoutingKey());
    }
}
