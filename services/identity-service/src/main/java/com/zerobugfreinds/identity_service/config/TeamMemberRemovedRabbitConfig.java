package com.zerobugfreinds.identity_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * team-service 도메인 이벤트 중 TEAM_MEMBER_REMOVED 수신용 큐 바인딩.
 */
@Configuration
public class TeamMemberRemovedRabbitConfig {

    @Bean
    public TopicExchange teamDomainEventExchange(
            @Value("${identity.team-event.exchange:team.events}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue identityTeamMemberRemovedQueue(
            @Value("${identity.team-member-removed.queue:identity.team.member-removed.queue}") String queueName
    ) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding identityTeamMemberRemovedBinding(
            Queue identityTeamMemberRemovedQueue,
            TopicExchange teamDomainEventExchange,
            @Value("${identity.team-member-removed.routing-key:team-member-added}") String routingKey
    ) {
        return BindingBuilder.bind(identityTeamMemberRemovedQueue)
                .to(teamDomainEventExchange)
                .with(routingKey);
    }
}
