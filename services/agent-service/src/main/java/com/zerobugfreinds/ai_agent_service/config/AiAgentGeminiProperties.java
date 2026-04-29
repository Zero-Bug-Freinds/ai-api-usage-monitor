package com.zerobugfreinds.ai_agent_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-agent.gemini")
public record AiAgentGeminiProperties(
		String apiKey,
		String model,
		String baseUrl
) {
}
