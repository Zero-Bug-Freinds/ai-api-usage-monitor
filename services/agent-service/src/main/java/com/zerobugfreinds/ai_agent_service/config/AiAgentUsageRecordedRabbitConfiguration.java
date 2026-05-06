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
		prefix = "ai-agent.rabbit.usage-recorded",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class AiAgentUsageRecordedRabbitConfiguration {

	@Bean
	public TopicExchange aiAgentUsageRecordedExchange(
			@Value("${ai-agent.rabbit.usage-recorded.exchange}") String exchange
	) {
		return new TopicExchange(exchange, true, false);
	}

	@Bean
	public Queue aiAgentUsageRecordedQueue(
			@Value("${ai-agent.rabbit.usage-recorded.queue}") String queue
	) {
		return new Queue(queue, true);
	}

	@Bean
	public Binding aiAgentUsageRecordedBinding(
			Queue aiAgentUsageRecordedQueue,
			TopicExchange aiAgentUsageRecordedExchange,
			@Value("${ai-agent.rabbit.usage-recorded.routing-key}") String routingKey
	) {
		return BindingBuilder.bind(aiAgentUsageRecordedQueue)
				.to(aiAgentUsageRecordedExchange)
				.with(routingKey);
	}
}
