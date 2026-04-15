package com.zerobugfreinds.team_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityAccountDeletionRabbitConfig {

	@Bean
	public TopicExchange identityAccountDeletionExchange(
			@Value("${identity.account-deletion-event.exchange:identity.events}") String exchangeName
	) {
		return new TopicExchange(exchangeName, true, false);
	}

	@Bean
	public Queue teamAccountDeletionRequestedQueue(
			@Value("${identity.account-deletion-event.team.queue:team.account-deletion.requested.queue}") String queueName
	) {
		return new Queue(queueName, true);
	}

	@Bean
	public Binding teamAccountDeletionRequestedBinding(
			Queue teamAccountDeletionRequestedQueue,
			TopicExchange identityAccountDeletionExchange,
			@Value("${identity.account-deletion-event.routing-key:identity.user.account-deletion-requested}") String routingKey
	) {
		return BindingBuilder.bind(teamAccountDeletionRequestedQueue)
				.to(identityAccountDeletionExchange)
				.with(routingKey);
	}
}
