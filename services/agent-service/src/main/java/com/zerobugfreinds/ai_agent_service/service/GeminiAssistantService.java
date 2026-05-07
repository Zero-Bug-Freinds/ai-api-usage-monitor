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

@Service
public class GeminiAssistantService {

	private static final Logger log = LoggerFactory.getLogger(GeminiAssistantService.class);
	private static final String DEFAULT_GEMINI_MODEL = "gemini-1.5-flash";
	private static final double FORECAST_TEMPERATURE = 0.0;
	private static final List<String> DEFAULT_ACTIONS = List.of("사용 패턴을 점검하고, 필요 시 예산 또는 모델 사용 한도를 조정하세요.");

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
		try {
			String inputJson = objectMapper.writeValueAsString(buildInputMap(request));
			String prompt = """
					You are a strictly analytical budget forecasting assistant for an AI API usage product.
					Do NOT output any conversational text.
					Output ONLY valid raw JSON.

					[Risk assessment rules]
					1) CRITICAL: utilization is over 90%%, OR the budget will likely run out before billing cycle end.
					2) WARNING: sudden spike in daily spend, or moderate utilization with trend risk.
					3) HEALTHY: utilization is low and remaining budget is sufficient, even if billing cycle end is near.

					[Example]
					Input: {"monthlyBudgetUsd":100,"currentSpendUsd":0.05,"billingCycleEndDate":"%s","recentDailySpendUsd":[0.01,0.01,0.01]}
					Output: {
					  "predictedRunOutDate": "%s",
					  "daysUntilRunOut": 999,
					  "healthStatus": "HEALTHY",
					  "budgetUtilizationPercent": "0.05",
					  "assistantMessage": "예산 사용량이 매우 적어 상태가 양호합니다.",
					  "recommendedActions": ["현재 추세를 유지하세요.", "주간 단위로 사용량만 점검하세요."]
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
					  "recommendedActions": ["<Korean action 1>", "<Korean action 2>"]
					}

					Today's date for reference: %s
					""".formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(999), inputJson, LocalDate.now());

			String responseBody = callGenerateContent(prompt);
			if (responseBody == null || responseBody.isBlank()) {
				log.warn("Gemini inference returned empty response body");
				return Optional.empty();
			}
			JsonNode root = objectMapper.readTree(responseBody);
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
			log.warn("Gemini inference failed: {}", ex.getMessage());
			return Optional.empty();
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

		String model = (properties.model() == null || properties.model().isBlank())
				? DEFAULT_GEMINI_MODEL
				: properties.model();
		String baseUrl = (properties.baseUrl() == null || properties.baseUrl().isBlank())
				? "https://generativelanguage.googleapis.com"
				: properties.baseUrl();
		try {
			return callGenerateContentWithModel(baseUrl, model, body);
		} catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 404 && !DEFAULT_GEMINI_MODEL.equals(model)) {
				log.warn("Gemini model {} not found. Retrying with fallback model {}", model, DEFAULT_GEMINI_MODEL);
				return callGenerateContentWithModel(baseUrl, DEFAULT_GEMINI_MODEL, body);
			}
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
		if (node.get("daysUntilRunOut").isIntegralNumber()) {
			long modelDays = node.get("daysUntilRunOut").asLong();
			if (Math.abs(modelDays - computedDays) <= 1) {
				daysUntilRunOut = modelDays;
			}
		}

		String health = textOrNull(node.get("healthStatus"));
		if (health == null) {
			return Optional.empty();
		}
		health = health.trim().toUpperCase();
		if (!"HEALTHY".equals(health) && !"WARNING".equals(health) && !"CRITICAL".equals(health)) {
			health = "WARNING";
		}

		BigDecimal utilization = parseBigDecimalFlexible(node.get("budgetUtilizationPercent"));
		if (utilization == null) {
			utilization = BigDecimal.ZERO;
		}
		utilization = utilization.setScale(2, RoundingMode.HALF_UP);

		String message = textOrNull(node.get("assistantMessage"));
		if (message == null || message.isBlank()) {
			message = "예산 소진 예측을 요약할 수 없습니다.";
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
			actions.addAll(DEFAULT_ACTIONS);
		}

		return Optional.of(new AiBudgetForecastResult(
				predicted,
				daysUntilRunOut,
				health,
				utilization,
				message.trim(),
				List.copyOf(actions)
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
}
