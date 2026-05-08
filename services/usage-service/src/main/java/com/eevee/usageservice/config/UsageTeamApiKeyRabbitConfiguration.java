package com.eevee.usageservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "usage.rabbit.team-api-key", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsageTeamApiKeyRabbitConfiguration {

    @Bean
    public TopicExchange usageTeamApiKeyExchange(UsageRabbitProperties props) {
        return new TopicExchange(props.getTeamApiKey().getExchange(), true, false);
    }

    @Bean
    public Queue usageTeamApiKeyQueue(UsageRabbitProperties props) {
        return new Queue(props.getTeamApiKey().getQueue(), true);
    }

    @Bean
    public Binding usageTeamApiKeyBinding(
            Queue usageTeamApiKeyQueue,
            TopicExchange usageTeamApiKeyExchange,
            UsageRabbitProperties props
    ) {
        return BindingBuilder.bind(usageTeamApiKeyQueue)
                .to(usageTeamApiKeyExchange)
                .with(props.getTeamApiKey().getRoutingKey());
    }
}
