package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiHttpClientConfiguration;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiProperties;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationConfidenceLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Service
public class RecommendationGeminiService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationGeminiService.class);

	private final AiAgentGeminiProperties properties;
	private final ObjectMapper objectMapper;
	private final AgentLlmCompletionClient llmCompletionClient;
	private final ExecutorService geminiBatchExecutor;

	public RecommendationGeminiService(
			AiAgentGeminiProperties properties,
			ObjectMapper objectMapper,
			AgentLlmCompletionClient llmCompletionClient,
			@Qualifier(AiAgentGeminiHttpClientConfiguration.GEMINI_BATCH_EXECUTOR) ExecutorService geminiBatchExecutor
	) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.llmCompletionClient = llmCompletionClient;
		this.geminiBatchExecutor = geminiBatchExecutor;
	}

	public Optional<AiRecommendationResult> inferRecommendation(AiRecommendationPromptRequest request) {
		if (!llmCompletionClient.geminiConfigured() && !llmCompletionClient.deepseekConfigured()) {
			log.warn("Recommendation LLM skipped: set AI_AGENT_DEEPSEEK_API_KEY and/or AI_AGENT_GEMINI_API_KEY (or GOOGLE_API_KEY chain for Gemini)");
			return Optional.empty();
		}
		String requestId = UUID.randomUUID().toString();
		String keyId = request.keyId() == null || request.keyId().isBlank() ? "unknown" : request.keyId().trim();
		UsageAggregate usageAggregate = new UsageAggregate(requestId, keyId);
		try {
			String inputJson = objectMapper.writeValueAsString(request);
			String prompt = """
					You are an AI model recommendation assistant for API usage optimization.
					Return ONLY one raw JSON object.
					No markdown fences.

					[Input]
					%s

					[Rules]
					- Explain recommendation rationale in Korean.
					- Use one of HIGH, MEDIUM, LOW for confidenceLevel.
					- Keep reasonMessage concise (<= 2 sentences).
					- title should be short.

					[Output JSON schema]
					{
					  "title": "string",
					  "reasonMessage": "string",
					  "confidenceLevel": "HIGH|MEDIUM|LOW",
					  "disclaimer": "string|null"
					}
					""".formatted(inputJson);
			Optional<AiRecommendationResult> primary = inferRecommendationByPrompt(requestId, keyId, 1, prompt, usageAggregate);
			if (primary.isPresent()) {
				return primary;
			}
			String retryPrompt = prompt + "\nDo not omit required keys. Return valid JSON only.";
			return inferRecommendationByPrompt(requestId, keyId, 2, retryPrompt, usageAggregate);
		} catch (Exception ex) {
			log.warn("Recommendation LLM inference failed: {}", ex.getMessage());
			return Optional.empty();
		} finally {
			logGeminiUsageSummary(usageAggregate);
		}
	}

	/**
	 * Runs one Gemini inference per key so recommendation prompts never mix multiple keys.
	 */
	public Map<String, AiRecommendationResult> inferRecommendations(List<AiRecommendationPromptRequest> requests) {
		if (requests == null || requests.isEmpty()) {
			return Map.of();
		}
		List<AiRecommendationPromptRequest> ordered = requests.stream()
				.filter(r -> r.keyId() != null && !r.keyId().isBlank())
				.toList();
		Map<String, AiRecommendationResult> byKeyId = new LinkedHashMap<>();
		if (ordered.isEmpty()) {
			return byKeyId;
		}
		int parallelism = properties.resolvedBatchParallelism();
		if (ordered.size() == 1 || parallelism <= 1) {
			for (AiRecommendationPromptRequest request : ordered) {
				String kid = request.keyId().trim();
				inferRecommendation(request).ifPresent(result -> byKeyId.put(kid, result));
			}
			return byKeyId;
		}
		Map<String, Optional<AiRecommendationResult>> pending = new ConcurrentHashMap<>();
		@SuppressWarnings("unchecked")
		CompletableFuture<Void>[] futures = ordered.stream()
				.map(request -> CompletableFuture.runAsync(() -> {
					String kid = request.keyId().trim();
					pending.put(kid, inferRecommendation(request));
				}, geminiBatchExecutor))
				.toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(futures).join();
		for (AiRecommendationPromptRequest request : ordered) {
			String kid = request.keyId().trim();
			pending.getOrDefault(kid, Optional.empty()).ifPresent(result -> byKeyId.put(kid, result));
		}
		return byKeyId;
	}

	private Optional<AiRecommendationResult> inferRecommendationByPrompt(
			String requestId,
			String keyId,
			int attempt,
			String prompt,
			UsageAggregate usageAggregate
	) {
		long startedAt = System.currentTimeMillis();
		try {
			String responseBody = callGenerateContent(prompt);
			if (responseBody == null || responseBody.isBlank()) {
				logGeminiUsage(requestId, keyId, attempt, prompt.length(), null, startedAt, usageAggregate);
				return Optional.empty();
			}
			JsonNode root = objectMapper.readTree(responseBody);
			logGeminiUsage(requestId, keyId, attempt, prompt.length(), root.path("usageMetadata"), startedAt, usageAggregate);
			JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			if (textNode.isMissingNode() || textNode.asText().isBlank()) {
				return Optional.empty();
			}
			String rawJson = extractJsonPayload(textNode.asText().trim());
			JsonNode recommendation = objectMapper.readTree(rawJson);
			String title = textOrNull(recommendation.get("title"));
			String reasonMessage = textOrNull(recommendation.get("reasonMessage"));
			String confidenceRaw = textOrNull(recommendation.get("confidenceLevel"));
			String disclaimer = textOrNull(recommendation.get("disclaimer"));
			if (title == null || title.isBlank() || reasonMessage == null || reasonMessage.isBlank() || confidenceRaw == null) {
				return Optional.empty();
			}
			RecommendationConfidenceLevel confidenceLevel;
			try {
				confidenceLevel = RecommendationConfidenceLevel.valueOf(confidenceRaw.trim().toUpperCase());
			} catch (Exception ex) {
				return Optional.empty();
			}
			return Optional.of(new AiRecommendationResult(
					title.trim(),
					reasonMessage.trim(),
					confidenceLevel,
					disclaimer == null || disclaimer.isBlank() ? null : disclaimer.trim()
			));
		} catch (Exception ex) {
			logGeminiUsage(requestId, keyId, attempt, prompt.length(), null, startedAt, usageAggregate);
			log.warn("Recommendation LLM attempt failed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private void logGeminiUsage(
			String requestId,
			String keyId,
			int attempt,
			int promptLen,
			JsonNode usageMetadata,
			long startedAt,
			UsageAggregate usageAggregate
	) {
		long latencyMs = Math.max(0L, System.currentTimeMillis() - startedAt);
		int promptTokens = usageMetadata == null ? -1 : usageMetadata.path("promptTokenCount").asInt(-1);
		int completionTokens = usageMetadata == null ? -1 : usageMetadata.path("candidatesTokenCount").asInt(-1);
		int totalTokens = usageMetadata == null ? -1 : usageMetadata.path("totalTokenCount").asInt(-1);
		usageAggregate.add(promptTokens, completionTokens, totalTokens);
		log.info(
				"[GEMINI_USAGE] reqId={} | keyId={} | attempt={} | promptLen={} | promptTok={} | compTok={} | totalTok={} | latencyMs={}",
				requestId,
				keyId,
				attempt,
				promptLen,
				promptTokens,
				completionTokens,
				totalTokens,
				latencyMs
		);
	}

	private void logGeminiUsageSummary(UsageAggregate usageAggregate) {
		log.info(
				"[GEMINI_USAGE_SUMMARY] reqId={} | keyId={} | calls={} | promptTokSum={} | compTokSum={} | totalTokSum={}",
				usageAggregate.requestId(),
				usageAggregate.keyId(),
				usageAggregate.calls(),
				usageAggregate.promptTokenSum(),
				usageAggregate.completionTokenSum(),
				usageAggregate.totalTokenSum()
		);
	}

	private static final class UsageAggregate {
		private final String requestId;
		private final String keyId;
		private int calls;
		private int promptTokenSum;
		private int completionTokenSum;
		private int totalTokenSum;

		private UsageAggregate(String requestId, String keyId) {
			this.requestId = requestId;
			this.keyId = keyId;
			this.calls = 0;
			this.promptTokenSum = 0;
			this.completionTokenSum = 0;
			this.totalTokenSum = 0;
		}

		private void add(int promptTokens, int completionTokens, int totalTokens) {
			this.calls += 1;
			if (promptTokens >= 0) {
				this.promptTokenSum += promptTokens;
			}
			if (completionTokens >= 0) {
				this.completionTokenSum += completionTokens;
			}
			if (totalTokens >= 0) {
				this.totalTokenSum += totalTokens;
			}
		}

		private String requestId() {
			return requestId;
		}

		private String keyId() {
			return keyId;
		}

		private int calls() {
			return calls;
		}

		private int promptTokenSum() {
			return promptTokenSum;
		}

		private int completionTokenSum() {
			return completionTokenSum;
		}

		private int totalTokenSum() {
			return totalTokenSum;
		}
	}

	private String callGenerateContent(String prompt) {
		return llmCompletionClient.completeAsGeminiGenerateContentJson(prompt, 0.0);
	}

	private static String textOrNull(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return null;
		}
		String t = n.asText();
		return t == null ? null : t;
	}

	private static String extractJsonPayload(String raw) {
		String t = raw.trim();
		if (t.startsWith("```")) {
			int firstNl = t.indexOf('\n');
			int fenceEnd = t.lastIndexOf("```");
			if (firstNl >= 0 && fenceEnd > firstNl) {
				t = t.substring(firstNl + 1, fenceEnd).trim();
				if (t.regionMatches(true, 0, "json", 0, 4)) {
					t = t.substring(4).trim();
				}
			}
		}
		return t;
	}

	public record AiRecommendationPromptRequest(
			String scopeType,
			String scopeId,
			String keyId,
			String reasonCode,
			String recommendationPriority,
			Long averageLatencyMs,
			String inputOutputRatio,
			Long totalReasoningTokens,
			Long totalRequests,
			Long totalTokensUsed,
			String currentMonthlyCostUsd,
			String recommendedMonthlyCostUsd,
			String estimatedSavingsPct,
			List<Map<String, String>> candidates
	) {
	}

	public record AiRecommendationResult(
			String title,
			String reasonMessage,
			RecommendationConfidenceLevel confidenceLevel,
			String disclaimer
	) {
	}
}
