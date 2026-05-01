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
		prefix = "ai-agent.rabbit.team-domain",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class AiAgentTeamDomainRabbitConfiguration {

	@Bean
	public TopicExchange aiAgentTeamDomainExchange(
			@Value("${ai-agent.rabbit.team-domain.exchange}") String exchange
	) {
		return new TopicExchange(exchange, true, false);
	}

	@Bean
	public Queue aiAgentTeamDomainQueue(
			@Value("${ai-agent.rabbit.team-domain.queue}") String queue
	) {
		return new Queue(queue, true);
	}

	@Bean
	public Binding aiAgentTeamDomainBinding(
			Queue aiAgentTeamDomainQueue,
			TopicExchange aiAgentTeamDomainExchange,
			@Value("${ai-agent.rabbit.team-domain.routing-key}") String routingKey
	) {
		return BindingBuilder.bind(aiAgentTeamDomainQueue)
				.to(aiAgentTeamDomainExchange)
				.with(routingKey);
	}
}
