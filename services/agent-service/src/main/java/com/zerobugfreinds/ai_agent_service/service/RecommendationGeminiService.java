package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiProperties;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationConfidenceLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
			Optional<AiRecommendationResult> primary = inferRecommendationByPrompt(prompt);
			if (primary.isPresent()) {
				return primary;
			}
			String retryPrompt = prompt + "\nDo not omit required keys. Return valid JSON only.";
			return inferRecommendationByPrompt(retryPrompt);
		} catch (Exception ex) {
			log.warn("Gemini recommendation inference failed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private Optional<AiRecommendationResult> inferRecommendationByPrompt(String prompt) {
		try {
			String responseBody = callGenerateContent(prompt);
			if (responseBody == null || responseBody.isBlank()) {
				return Optional.empty();
			}
			JsonNode root = objectMapper.readTree(responseBody);
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
			log.warn("Gemini recommendation attempt failed: {}", ex.getMessage());
			return Optional.empty();
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
