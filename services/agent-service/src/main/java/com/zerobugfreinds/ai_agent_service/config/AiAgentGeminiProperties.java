package com.zerobugfreinds.ai_agent_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-agent.gemini")
public record AiAgentGeminiProperties(
		String apiKey,
		String model,
		String baseUrl,
		Integer connectTimeoutMs,
		Integer readTimeoutMs,
		Integer batchParallelism
) {
	public int resolvedConnectTimeoutMs() {
		return connectTimeoutMs == null ? 5_000 : Math.max(1, connectTimeoutMs);
	}

	public int resolvedReadTimeoutMs() {
		return readTimeoutMs == null ? 120_000 : Math.max(1, readTimeoutMs);
	}

	public int resolvedBatchParallelism() {
		int p = batchParallelism == null ? 4 : batchParallelism;
		return Math.max(1, Math.min(p, 32));
	}
}
