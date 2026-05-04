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
		prefix = "ai-agent.rabbit.usage-prediction",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class AiAgentUsagePredictionRabbitConfiguration {

	@Bean
	public TopicExchange aiAgentUsagePredictionExchange(
			@Value("${ai-agent.rabbit.usage-prediction.exchange}") String exchange
	) {
		return new TopicExchange(exchange, true, false);
	}

	@Bean
	public Queue aiAgentUsagePredictionQueue(
			@Value("${ai-agent.rabbit.usage-prediction.queue}") String queue
	) {
		return new Queue(queue, true);
	}

	@Bean
	public Binding aiAgentUsagePredictionBinding(
			Queue aiAgentUsagePredictionQueue,
			TopicExchange aiAgentUsagePredictionExchange,
			@Value("${ai-agent.rabbit.usage-prediction.routing-key}") String routingKey
	) {
		return BindingBuilder.bind(aiAgentUsagePredictionQueue)
				.to(aiAgentUsagePredictionExchange)
				.with(routingKey);
	}
}
