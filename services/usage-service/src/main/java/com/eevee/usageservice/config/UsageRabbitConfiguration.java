package com.eevee.usageservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the consumer queue and binds it to the same topic exchange + routing key
 * that {@code proxy-service} uses when publishing {@code UsageRecordedEvent}.
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
    public Binding usageBinding(
            Queue usageQueue,
            TopicExchange usageExchange,
            UsageRabbitProperties usageRabbitProperties
    ) {
        return BindingBuilder.bind(usageQueue)
                .to(usageExchange)
                .with(usageRabbitProperties.getRoutingKey());
    }
}
