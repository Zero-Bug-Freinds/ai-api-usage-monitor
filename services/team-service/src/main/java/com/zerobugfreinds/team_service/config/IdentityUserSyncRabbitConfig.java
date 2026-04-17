package com.zerobugfreinds.team_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityUserSyncRabbitConfig {

    @Bean
    public TopicExchange identityUserSyncExchange(
            @Value("${identity.user-sync.exchange:identity.events}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue teamIdentityUserSyncQueue(
            @Value("${identity.user-sync.queue:team.identity.user-sync.queue}") String queueName
    ) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding teamIdentityUserSyncBinding(
            Queue teamIdentityUserSyncQueue,
            TopicExchange identityUserSyncExchange,
            @Value("${identity.user-sync.routing-key:identity.user.sync}") String routingKey
    ) {
        return BindingBuilder.bind(teamIdentityUserSyncQueue)
                .to(identityUserSyncExchange)
                .with(routingKey);
    }
}
