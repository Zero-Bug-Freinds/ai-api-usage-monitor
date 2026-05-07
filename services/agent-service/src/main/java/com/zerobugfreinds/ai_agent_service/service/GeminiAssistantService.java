package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiProperties;
import com.zerobugfreinds.ai_agent_service.dto.AiBudgetForecastResult;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class GeminiAssistantService {

	private static final Logger log = LoggerFactory.getLogger(GeminiAssistantService.class);
	private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
	private static final double FORECAST_TEMPERATURE = 0.0;

	private final AiAgentGeminiProperties properties;
	private final ObjectMapper objectMapper;

	public GeminiAssistantService(AiAgentGeminiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	/**
	 * Calls Gemini with the full request payload and expects a single JSON object with forecast fields.
	 * Returns empty when API key is missing, HTTP/parse fails, or the model output is invalid.
	 */
	public Optional<AiBudgetForecastResult> inferForecast(BudgetForecastRequest request) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			log.warn("Gemini inference skipped: API key missing");
			return Optional.empty();
		}
		String requestId = UUID.randomUUID().toString();
		String keyId = request.keyId() == null ? "unknown" : String.valueOf(request.keyId());
		UsageAggregate usageAggregate = new UsageAggregate(requestId, keyId);
		try {
			String inputJson = objectMapper.writeValueAsString(buildInputMap(request));
			String prompt = buildForecastPrompt(inputJson, false);
			Optional<AiBudgetForecastResult> primary = inferByPrompt(requestId, keyId, 1, prompt, usageAggregate);
			if (primary.isPresent()) {
				return primary;
			}
			// Keep AI-only policy while improving connectivity: second try with stricter JSON instructions.
			String retryPrompt = buildForecastPrompt(inputJson, true);
			Optional<AiBudgetForecastResult> secondary = inferByPrompt(requestId, keyId, 2, retryPrompt, usageAggregate);
			if (secondary.isPresent()) {
				return secondary;
			}
			log.warn("Gemini inference failed after retry: invalid payload shape");
			return Optional.empty();
		} catch (Exception ex) {
			log.warn("Gemini inference failed: {}", ex.getMessage());
			return Optional.empty();
		} finally {
			logGeminiUsageSummary(usageAggregate);
		}
	}

	public Map<Long, AiBudgetForecastResult> inferForecasts(List<BudgetForecastRequest> requests) {
		if (requests == null || requests.isEmpty()) {
			return Map.of();
		}
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			log.warn("Gemini batch inference skipped: API key missing");
			return Map.of();
		}
		String requestId = UUID.randomUUID().toString();
		UsageAggregate usageAggregate = new UsageAggregate(requestId, "batch");
		try {
			List<Map<String, Object>> payload = requests.stream()
					.map(this::buildBatchInputMap)
					.toList();
			String inputJson = objectMapper.writeValueAsString(payload);
			String prompt = buildBatchForecastPrompt(inputJson, false);
			Map<Long, AiBudgetForecastResult> primary = inferBatchByPrompt(requestId, 1, prompt, usageAggregate);
			if (!primary.isEmpty()) {
				return primary;
			}
			String retryPrompt = buildBatchForecastPrompt(inputJson, true);
			return inferBatchByPrompt(requestId, 2, retryPrompt, usageAggregate);
		} catch (Exception ex) {
			log.warn("Gemini batch inference failed: {}", ex.getMessage());
			return Map.of();
		} finally {
			logGeminiUsageSummary(usageAggregate);
		}
	}


	private String buildForecastPrompt(String inputJson, boolean retryMode) {
		String retryDirective = retryMode
				? """
					[Retry note]
					Previous output was invalid.
					You MUST include all required keys with valid values.
					Do not omit keys. Do not use null.
					"""
				: "";
		return """
					You are a strictly analytical budget forecasting assistant for an AI API usage product.
					Do NOT output any conversational text.
					Output ONLY valid raw JSON.

					[Risk assessment rules]
					1) CRITICAL: utilization is over 90%%, OR the budget will likely run out before billing cycle end.
					2) WARNING: sudden spike in daily spend, or moderate utilization with trend risk.
					3) HEALTHY: utilization is low and remaining budget is sufficient, even if billing cycle end is near.
					4) Detect anomalies from hourlyTokenUsage24h and recentDailyTokenUsage7d.
					5) Propose routing optimization from modelUsageDistribution7d.

					[Example]
					Input: {"monthlyBudgetUsd":100,"currentSpendUsd":0.05,"billingCycleEndDate":"%s","recentDailySpendUsd":[0.01,0.01,0.01]}
					Output: {
					  "predictedRunOutDate": "%s",
					  "daysUntilRunOut": 999,
					  "healthStatus": "HEALTHY",
					  "budgetUtilizationPercent": "0.05",
					  "assistantMessage": "예산 사용량이 매우 적어 상태가 양호합니다.",
					  "recommendedActions": ["현재 추세를 유지하세요.", "주간 단위로 사용량만 점검하세요."],
					  "anomalySummary": "유의미한 이상 징후가 관찰되지 않았습니다.",
					  "routingRecommendation": "현재 모델 구성이 안정적입니다.",
					  "estimatedRoutingSavingsPercent": "0.00"
					}

					[Actual input JSON]
					%s

					[Required output format]
					Return ONLY one raw JSON object.
					Never wrap in markdown (no ```json).
					Never add extra keys.
					{
					  "predictedRunOutDate": "yyyy-MM-dd",
					  "daysUntilRunOut": <integer>,
					  "healthStatus": "CRITICAL|WARNING|HEALTHY",
					  "budgetUtilizationPercent": "<string with 2 decimals>",
					  "assistantMessage": "<one Korean sentence>",
					  "recommendedActions": ["<Korean action 1>", "<Korean action 2>"],
					  "anomalySummary": "<Korean one sentence anomaly finding>",
					  "routingRecommendation": "<Korean one sentence model routing suggestion>",
					  "estimatedRoutingSavingsPercent": "<string with 2 decimals>"
					}

					Today's date for reference: %s
					%s
					""".formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(999), inputJson, LocalDate.now(), retryDirective);
	}

	private String buildBatchForecastPrompt(String inputJson, boolean retryMode) {
		String retryDirective = retryMode
				? """
					[Retry note]
					Previous output was invalid.
					You MUST return a valid JSON array.
					Every item must include keyId and all required fields.
					"""
				: "";
		return """
					You are a strictly analytical budget forecasting assistant for an AI API usage product.
					Do NOT output any conversational text.
					Output ONLY valid raw JSON array.

					[Task]
					Analyze each input object independently and return one result object per input object.
					Preserve keyId exactly for mapping.

					[Input JSON array]
					%s

					[Required output format]
					Return ONLY one raw JSON array.
					Never wrap in markdown (no ```json).
					Each item must contain exactly:
					{
					  "keyId": <long>,
					  "predictedRunOutDate": "yyyy-MM-dd",
					  "daysUntilRunOut": <integer>,
					  "healthStatus": "CRITICAL|WARNING|HEALTHY",
					  "budgetUtilizationPercent": "<string with 2 decimals>",
					  "assistantMessage": "<one Korean sentence>",
					  "recommendedActions": ["<Korean action 1>", "<Korean action 2>"],
					  "anomalySummary": "<Korean one sentence anomaly finding>",
					  "routingRecommendation": "<Korean one sentence model routing suggestion>",
					  "estimatedRoutingSavingsPercent": "<string with 2 decimals>"
					}

					Today's date for reference: %s
					%s
					""".formatted(inputJson, LocalDate.now(), retryDirective);
	}

	private Optional<AiBudgetForecastResult> inferByPrompt(
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
				log.warn("Gemini inference returned empty response body");
				return Optional.empty();
			}
			JsonNode root = objectMapper.readTree(responseBody);
			logGeminiUsage(requestId, keyId, attempt, prompt.length(), root.path("usageMetadata"), startedAt, usageAggregate);
			JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			if (textNode.isMissingNode() || textNode.asText().isBlank()) {
				String blockReason = root.path("promptFeedback").path("blockReason").asText("");
				if (!blockReason.isBlank()) {
					log.warn("Gemini response blocked: blockReason={}", blockReason);
				} else {
					log.warn("Gemini response missing text candidate: summary={}", summarizeResponseBody(responseBody));
				}
				return Optional.empty();
			}
			String rawJson = extractJsonPayload(textNode.asText().trim());
			JsonNode forecast = objectMapper.readTree(rawJson);
			return parseAiForecast(forecast);
		} catch (Exception ex) {
			logGeminiUsage(requestId, keyId, attempt, prompt.length(), null, startedAt, usageAggregate);
			log.warn("Gemini inference attempt failed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private Map<Long, AiBudgetForecastResult> inferBatchByPrompt(
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
				log.warn("Gemini batch inference returned empty response body");
				return Map.of();
			}
			JsonNode root = objectMapper.readTree(responseBody);
			logGeminiUsage(requestId, "batch", attempt, prompt.length(), root.path("usageMetadata"), startedAt, usageAggregate);
			JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			if (textNode.isMissingNode() || textNode.asText().isBlank()) {
				String blockReason = root.path("promptFeedback").path("blockReason").asText("");
				if (!blockReason.isBlank()) {
					log.warn("Gemini batch response blocked: blockReason={}", blockReason);
				} else {
					log.warn("Gemini batch response missing text candidate: summary={}", summarizeResponseBody(responseBody));
				}
				return Map.of();
			}
			String rawJson = extractJsonPayload(textNode.asText().trim());
			JsonNode forecastArray = objectMapper.readTree(rawJson);
			if (!forecastArray.isArray()) {
				return Map.of();
			}
			Map<Long, AiBudgetForecastResult> results = new LinkedHashMap<>();
			for (JsonNode item : forecastArray) {
				if (!item.isObject()) {
					continue;
				}
				JsonNode keyIdNode = item.get("keyId");
				if (keyIdNode == null || !keyIdNode.canConvertToLong()) {
					continue;
				}
				Optional<AiBudgetForecastResult> parsed = parseAiForecast(item);
				if (parsed.isEmpty()) {
					continue;
				}
				results.put(keyIdNode.asLong(), parsed.get());
			}
			return results;
		} catch (Exception ex) {
			logGeminiUsage(requestId, "batch", attempt, prompt.length(), null, startedAt, usageAggregate);
			log.warn("Gemini batch inference attempt failed: {}", ex.getMessage());
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

	private Map<String, Object> buildInputMap(BudgetForecastRequest request) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("userId", request.userId());
		map.put("monthlyBudgetUsd", request.monthlyBudgetUsd());
		map.put("currentSpendUsd", request.currentSpendUsd());
		map.put("remainingTokens", request.remainingTokens());
		map.put("averageDailyTokenUsage", request.averageDailyTokenUsage());
		map.put("averageDailySpendUsd", request.averageDailySpendUsd());
		map.put("billingCycleEndDate", request.billingCycleEndDate() != null ? request.billingCycleEndDate().toString() : null);
		map.put("recentDailySpendUsd", request.recentDailySpendUsd() != null ? request.recentDailySpendUsd() : List.of());
		map.put("recentDailyTokenUsage7d", request.recentDailyTokenUsage7d() != null ? request.recentDailyTokenUsage7d() : List.of());
		map.put("modelUsageDistribution7d", request.modelUsageDistribution7d() != null ? request.modelUsageDistribution7d() : List.of());
		map.put("hourlyTokenUsage24h", request.hourlyTokenUsage24h() != null ? request.hourlyTokenUsage24h() : List.of());
		return map;
	}

	private Map<String, Object> buildBatchInputMap(BudgetForecastRequest request) {
		Map<String, Object> map = new LinkedHashMap<>(buildInputMap(request));
		map.put("keyId", request.keyId());
		return map;
	}

	private String callGenerateContent(String prompt) {
		Map<String, Object> body = Map.of(
				"contents", new Object[] {
						Map.of("parts", new Object[] {Map.of("text", prompt)})
				},
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"temperature", FORECAST_TEMPERATURE
				)
		);

		String configuredModel = (properties.model() == null || properties.model().isBlank())
				? DEFAULT_GEMINI_MODEL
				: properties.model().trim();
		String baseUrl = (properties.baseUrl() == null || properties.baseUrl().isBlank())
				? "https://generativelanguage.googleapis.com"
				: properties.baseUrl();
		try {
			return callGenerateContentWithModel(baseUrl, configuredModel, body);
		} catch (RestClientResponseException ex) {
			log.warn(
					"Gemini call failed. model={}, status={}, responseBody={}",
					configuredModel,
					ex.getStatusCode(),
					summarizeErrorBody(ex.getResponseBodyAsString())
			);
			throw ex;
		}
	}

	private String callGenerateContentWithModel(String baseUrl, String model, Map<String, Object> body) {
		String uri = baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + properties.apiKey();
		return RestClient.create()
				.post()
				.uri(uri)
				.body(body)
				.retrieve()
				.body(String.class);
	}

	private Optional<AiBudgetForecastResult> parseAiForecast(JsonNode node) {
		if (node == null || node.isMissingNode() || !node.isObject()) {
			return Optional.empty();
		}
		String dateStr = textOrNull(node.get("predictedRunOutDate"));
		if (dateStr == null || dateStr.isBlank()) {
			return Optional.empty();
		}
		LocalDate predicted;
		try {
			predicted = LocalDate.parse(dateStr.trim());
		} catch (Exception ex) {
			return Optional.empty();
		}
		LocalDate today = LocalDate.now();
		long computedDays = Math.max(0, ChronoUnit.DAYS.between(today, predicted));

		long daysUntilRunOut = computedDays;
		if (!node.has("daysUntilRunOut") || !node.get("daysUntilRunOut").isIntegralNumber()) {
			return Optional.empty();
		}
		long modelDays = node.get("daysUntilRunOut").asLong();
		if (Math.abs(modelDays - computedDays) > 1) {
			return Optional.empty();
		}
		daysUntilRunOut = modelDays;

		String health = textOrNull(node.get("healthStatus"));
		if (health == null) {
			return Optional.empty();
		}
		health = health.trim().toUpperCase();
		if (!"HEALTHY".equals(health) && !"WARNING".equals(health) && !"CRITICAL".equals(health)) {
			return Optional.empty();
		}

		BigDecimal utilization = parseBigDecimalFlexible(node.get("budgetUtilizationPercent"));
		if (utilization == null) {
			return Optional.empty();
		}
		utilization = utilization.setScale(2, RoundingMode.HALF_UP);

		String message = textOrNull(node.get("assistantMessage"));
		if (message == null || message.isBlank()) {
			return Optional.empty();
		}

		List<String> actions = new ArrayList<>();
		JsonNode arr = node.get("recommendedActions");
		if (arr != null && arr.isArray()) {
			for (JsonNode item : arr) {
				String line = textOrNull(item);
				if (line != null && !line.isBlank()) {
					actions.add(line.trim());
				}
			}
		}
		if (actions.isEmpty()) {
			return Optional.empty();
		}

		String anomalySummary = textOrNull(node.get("anomalySummary"));
		if (anomalySummary == null || anomalySummary.isBlank()) {
			return Optional.empty();
		}
		String routingRecommendation = textOrNull(node.get("routingRecommendation"));
		if (routingRecommendation == null || routingRecommendation.isBlank()) {
			return Optional.empty();
		}
		BigDecimal estimatedRoutingSavingsPercent = parseBigDecimalFlexible(node.get("estimatedRoutingSavingsPercent"));
		if (estimatedRoutingSavingsPercent == null || estimatedRoutingSavingsPercent.signum() < 0) {
			return Optional.empty();
		}
		estimatedRoutingSavingsPercent = estimatedRoutingSavingsPercent.setScale(2, RoundingMode.HALF_UP);

		return Optional.of(new AiBudgetForecastResult(
				predicted,
				daysUntilRunOut,
				health,
				utilization,
				message.trim(),
				List.copyOf(actions),
				anomalySummary.trim(),
				routingRecommendation.trim(),
				estimatedRoutingSavingsPercent
		));
	}

	private static String textOrNull(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return null;
		}
		String t = n.asText();
		return t == null ? null : t;
	}

	private static BigDecimal parseBigDecimalFlexible(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) {
			return null;
		}
		try {
			if (n.isNumber()) {
				return BigDecimal.valueOf(n.asDouble()).setScale(2, RoundingMode.HALF_UP);
			}
			String s = n.asText().trim();
			if (s.isEmpty()) {
				return null;
			}
			return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
		} catch (Exception ex) {
			return null;
		}
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

	private static String summarizeResponseBody(String body) {
		String compact = body.replaceAll("\\s+", " ").trim();
		if (compact.length() <= 280) {
			return compact;
		}
		return compact.substring(0, 280) + "...";
	}

	private static String summarizeErrorBody(String body) {
		if (body == null) {
			return "";
		}
		String compact = body.replaceAll("\\s+", " ").trim();
		if (compact.length() <= 600) {
			return compact;
		}
		return compact.substring(0, 600) + "...";
	}
}
