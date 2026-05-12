package com.zerobugfreinds.ai_agent_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-agent.deepseek")
public record AiAgentDeepseekProperties(
		String apiKey,
		String baseUrl,
		String model,
		Integer connectTimeoutMs,
		Integer readTimeoutMs
) {
	public int resolvedConnectTimeoutMs() {
		return connectTimeoutMs == null ? 5_000 : Math.max(1, connectTimeoutMs);
	}

	public int resolvedReadTimeoutMs() {
		return readTimeoutMs == null ? 120_000 : Math.max(1, readTimeoutMs);
	}

	public String resolvedBaseUrl() {
		if (baseUrl == null || baseUrl.isBlank()) {
			return "https://api.deepseek.com";
		}
		String b = baseUrl.trim();
		return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
	}

	public String resolvedModel() {
		if (model == null || model.isBlank()) {
			return "deepseek-chat";
		}
		return model.trim();
	}
}
