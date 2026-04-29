package com.zerobugfreinds.ai_agent_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiAgentBillingRabbitConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-cost", name = "enabled", havingValue = "true", matchIfMissing = true)
	public TopicExchange aiAgentBillingCostExchange(AiAgentBillingRabbitProperties props) {
		return new TopicExchange(props.billingCost().exchange(), true, false);
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-cost", name = "enabled", havingValue = "true", matchIfMissing = true)
	public Queue aiAgentBillingCostQueue(AiAgentBillingRabbitProperties props) {
		return new Queue(props.billingCost().queue(), true);
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-cost", name = "enabled", havingValue = "true", matchIfMissing = true)
	public Binding aiAgentBillingCostBinding(
			Queue aiAgentBillingCostQueue,
			TopicExchange aiAgentBillingCostExchange,
			AiAgentBillingRabbitProperties props
	) {
		return BindingBuilder.bind(aiAgentBillingCostQueue)
				.to(aiAgentBillingCostExchange)
				.with(props.billingCost().routingKey());
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-budget", name = "enabled", havingValue = "true", matchIfMissing = true)
	public TopicExchange aiAgentBillingBudgetExchange(AiAgentBillingRabbitProperties props) {
		return new TopicExchange(props.billingBudget().exchange(), true, false);
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-budget", name = "enabled", havingValue = "true", matchIfMissing = true)
	public Queue aiAgentBillingBudgetQueue(AiAgentBillingRabbitProperties props) {
		return new Queue(props.billingBudget().queue(), true);
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai-agent.rabbit.billing-budget", name = "enabled", havingValue = "true", matchIfMissing = true)
	public Binding aiAgentBillingBudgetBinding(
			Queue aiAgentBillingBudgetQueue,
			TopicExchange aiAgentBillingBudgetExchange,
			AiAgentBillingRabbitProperties props
	) {
		return BindingBuilder.bind(aiAgentBillingBudgetQueue)
				.to(aiAgentBillingBudgetExchange)
				.with(props.billingBudget().routingKey());
	}
}
