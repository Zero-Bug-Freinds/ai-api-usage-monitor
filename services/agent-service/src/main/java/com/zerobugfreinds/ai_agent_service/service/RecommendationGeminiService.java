package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiProperties;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationConfidenceLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecommendationGeminiService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationGeminiService.class);
	private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";

	private final AiAgentGeminiProperties properties;
	private final ObjectMapper objectMapper;

	public RecommendationGeminiService(AiAgentGeminiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public Optional<AiRecommendationResult> inferRecommendation(AiRecommendationPromptRequest request) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			log.warn("Gemini recommendation skipped: API key missing");
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
			log.warn("Gemini recommendation inference failed: {}", ex.getMessage());
			return Optional.empty();
		} finally {
			logGeminiUsageSummary(usageAggregate);
		}
	}

	public Map<String, AiRecommendationResult> inferRecommendations(List<AiRecommendationPromptRequest> requests) {
		if (requests == null || requests.isEmpty()) {
			return Map.of();
		}
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			log.warn("Gemini recommendation batch skipped: API key missing");
			return Map.of();
		}
		String requestId = UUID.randomUUID().toString();
		UsageAggregate usageAggregate = new UsageAggregate(requestId, "batch");
		try {
			String inputJson = objectMapper.writeValueAsString(requests);
			String prompt = """
					You are an AI model recommendation assistant for API usage optimization.
					Return ONLY one raw JSON array.
					No markdown fences.

					[Input array]
					%s

					[Rules]
					- Analyze each item independently.
					- Preserve every input keyId in output.
					- Explain recommendation rationale in Korean.
					- Use one of HIGH, MEDIUM, LOW for confidenceLevel.
					- Keep reasonMessage concise (<= 2 sentences).
					- title should be short.

					[Output JSON schema]
					[
					  {
					    "keyId": "string",
					    "title": "string",
					    "reasonMessage": "string",
					    "confidenceLevel": "HIGH|MEDIUM|LOW",
					    "disclaimer": "string|null"
					  }
					]
					""".formatted(inputJson);
			Map<String, AiRecommendationResult> primary = inferRecommendationsByPrompt(requestId, 1, prompt, usageAggregate);
			if (!primary.isEmpty()) {
				return primary;
			}
			String retryPrompt = prompt + "\nDo not omit keyId and required keys. Return valid JSON array only.";
			return inferRecommendationsByPrompt(requestId, 2, retryPrompt, usageAggregate);
		} catch (Exception ex) {
			log.warn("Gemini recommendation batch inference failed: {}", ex.getMessage());
			return Map.of();
		} finally {
			logGeminiUsageSummary(usageAggregate);
		}
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
			log.warn("Gemini recommendation attempt failed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private Map<String, AiRecommendationResult> inferRecommendationsByPrompt(
			String requestId,
			int attempt,
			String prompt,
			UsageAggregate usageAggregate
	) {
		long startedAt = System.currentTimeMillis();
		try {
			String responseBody = callGenerateContent(prompt);
			if (responseBody == null || responseBody.isBlank()) {
				logGeminiUsage(requestId, "batch", attempt, prompt.length(), null, startedAt, usageAggregate);
				return Map.of();
			}
			JsonNode root = objectMapper.readTree(responseBody);
			logGeminiUsage(requestId, "batch", attempt, prompt.length(), root.path("usageMetadata"), startedAt, usageAggregate);
			JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			if (textNode.isMissingNode() || textNode.asText().isBlank()) {
				return Map.of();
			}
			String rawJson = extractJsonPayload(textNode.asText().trim());
			JsonNode arrayNode = objectMapper.readTree(rawJson);
			if (!arrayNode.isArray()) {
				return Map.of();
			}
			Map<String, AiRecommendationResult> byKeyId = new LinkedHashMap<>();
			for (JsonNode recommendation : arrayNode) {
				String keyId = textOrNull(recommendation.get("keyId"));
				String title = textOrNull(recommendation.get("title"));
				String reasonMessage = textOrNull(recommendation.get("reasonMessage"));
				String confidenceRaw = textOrNull(recommendation.get("confidenceLevel"));
				String disclaimer = textOrNull(recommendation.get("disclaimer"));
				if (keyId == null || keyId.isBlank()) {
					continue;
				}
				if (title == null || title.isBlank() || reasonMessage == null || reasonMessage.isBlank() || confidenceRaw == null) {
					continue;
				}
				RecommendationConfidenceLevel confidenceLevel;
				try {
					confidenceLevel = RecommendationConfidenceLevel.valueOf(confidenceRaw.trim().toUpperCase());
				} catch (Exception ex) {
					continue;
				}
				byKeyId.put(
						keyId.trim(),
						new AiRecommendationResult(
								title.trim(),
								reasonMessage.trim(),
								confidenceLevel,
								disclaimer == null || disclaimer.isBlank() ? null : disclaimer.trim()
						)
				);
			}
			return byKeyId;
		} catch (Exception ex) {
			logGeminiUsage(requestId, "batch", attempt, prompt.length(), null, startedAt, usageAggregate);
			log.warn("Gemini recommendation batch attempt failed: {}", ex.getMessage());
			return Map.of();
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
		Map<String, Object> body = Map.of(
				"contents", new Object[]{
						Map.of("parts", new Object[]{Map.of("text", prompt)})
				},
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"temperature", 0.0
				)
		);
		String configuredModel = (properties.model() == null || properties.model().isBlank())
				? DEFAULT_GEMINI_MODEL
				: properties.model().trim();
		String baseUrl = (properties.baseUrl() == null || properties.baseUrl().isBlank())
				? "https://generativelanguage.googleapis.com"
				: properties.baseUrl();
		String uri = baseUrl + "/v1beta/models/" + configuredModel + ":generateContent?key=" + properties.apiKey();
		return RestClient.create()
				.post()
				.uri(uri)
				.body(body)
				.retrieve()
				.body(String.class);
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
			Long averageLatencyMs,
			String inputOutputRatio,
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
