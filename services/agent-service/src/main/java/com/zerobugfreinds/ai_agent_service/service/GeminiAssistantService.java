package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiProperties;
import com.zerobugfreinds.ai_agent_service.dto.AiBudgetForecastResult;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
			return Optional.empty();
		}
		try {
			String inputJson = objectMapper.writeValueAsString(buildInputMap(request));
			String prompt = """
					You are a budget forecasting assistant for an AI API usage product.
					Given the JSON input, estimate when the user's monthly budget is likely to run out and assess risk.

					Input JSON:
					%s

					Respond with ONLY one raw JSON object (no markdown code fences, no commentary). Use these keys exactly:
					- predictedRunOutDate: string yyyy-MM-dd (date when budget is expected to be exhausted; if already exhausted use today in UTC+9 sense as calendar "today")
					- daysUntilRunOut: integer >= 0, calendar days from today until predictedRunOutDate (must be consistent with predictedRunOutDate)
					- healthStatus: one of HEALTHY, WARNING, CRITICAL
					- budgetUtilizationPercent: string with two decimal places, e.g. "72.50" (estimated current utilization percent)
					- assistantMessage: one short Korean sentence summarizing the situation for the user
					- recommendedActions: JSON array of 2-5 short Korean action strings

					Today's date for reference: %s
					""".formatted(inputJson, LocalDate.now());

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
			JsonNode forecast = objectMapper.readTree(rawJson);
			return parseAiForecast(forecast);
		} catch (Exception ignored) {
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
				}
		);

		String model = (properties.model() == null || properties.model().isBlank())
				? "gemini-1.5-flash"
				: properties.model();
		String baseUrl = (properties.baseUrl() == null || properties.baseUrl().isBlank())
				? "https://generativelanguage.googleapis.com"
				: properties.baseUrl();
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
}
