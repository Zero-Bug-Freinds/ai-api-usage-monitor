package com.zerobugfreinds.ai_agent_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
		prefix = "ai-agent.rabbit.team-api-key",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class AiAgentTeamApiKeyRabbitConfiguration {

	@Bean
	public TopicExchange aiAgentTeamApiKeyExchange(
			@Value("${ai-agent.rabbit.team-api-key.exchange}") String exchange
	) {
		return new TopicExchange(exchange, true, false);
	}

	@Bean
	public Queue aiAgentTeamApiKeyQueue(
			@Value("${ai-agent.rabbit.team-api-key.queue}") String queue
	) {
		return new Queue(queue, true);
	}

	@Bean
	public Binding aiAgentTeamApiKeyBinding(
			Queue aiAgentTeamApiKeyQueue,
			TopicExchange aiAgentTeamApiKeyExchange,
			@Value("${ai-agent.rabbit.team-api-key.routing-key}") String routingKey
	) {
		return BindingBuilder.bind(aiAgentTeamApiKeyQueue)
				.to(aiAgentTeamApiKeyExchange)
				.with(routingKey);
	}
}
