package com.eevee.usageservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "usage.rabbit.identity-api-key", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsageIdentityApiKeyRabbitConfiguration {

    @Bean
    public TopicExchange usageIdentityApiKeyExchange(UsageRabbitProperties props) {
        return new TopicExchange(props.getIdentityApiKey().getExchange(), true, false);
    }

    @Bean
    public Queue usageIdentityApiKeyQueue(UsageRabbitProperties props) {
        return new Queue(props.getIdentityApiKey().getQueue(), true);
    }

    @Bean
    public Binding usageIdentityApiKeyBinding(
            Queue usageIdentityApiKeyQueue,
            TopicExchange usageIdentityApiKeyExchange,
            UsageRabbitProperties props
    ) {
        return BindingBuilder.bind(usageIdentityApiKeyQueue)
                .to(usageIdentityApiKeyExchange)
                .with(props.getIdentityApiKey().getRoutingKey());
    }
}
