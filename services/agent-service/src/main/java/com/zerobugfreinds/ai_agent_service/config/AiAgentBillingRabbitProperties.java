package com.zerobugfreinds.ai_agent_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-agent.rabbit")
public record AiAgentBillingRabbitProperties(
		Channel billingCost,
		Channel billingBudget
) {
	public record Channel(
			boolean enabled,
			String exchange,
			String routingKey,
			String queue
	) {
	}
}
