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
		prefix = "ai-agent.rabbit.daily-cumulative-tokens",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class AiAgentDailyCumulativeTokensRabbitConfiguration {

	@Bean
	public TopicExchange aiAgentDailyCumulativeTokensExchange(
			@Value("${ai-agent.rabbit.daily-cumulative-tokens.exchange}") String exchange
	) {
		return new TopicExchange(exchange, true, false);
	}

	@Bean
	public Queue aiAgentDailyCumulativeTokensQueue(
			@Value("${ai-agent.rabbit.daily-cumulative-tokens.queue}") String queue
	) {
		return new Queue(queue, true);
	}

	@Bean
	public Binding aiAgentDailyCumulativeTokensBinding(
			Queue aiAgentDailyCumulativeTokensQueue,
			TopicExchange aiAgentDailyCumulativeTokensExchange,
			@Value("${ai-agent.rabbit.daily-cumulative-tokens.routing-key}") String routingKey
	) {
		return BindingBuilder.bind(aiAgentDailyCumulativeTokensQueue)
				.to(aiAgentDailyCumulativeTokensExchange)
				.with(routingKey);
	}
}
