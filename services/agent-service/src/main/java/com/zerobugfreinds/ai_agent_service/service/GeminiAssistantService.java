package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiProperties;
import com.zerobugfreinds.ai_agent_service.dto.BudgetForecastRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class GeminiAssistantService {

	private final AiAgentGeminiProperties properties;
	private final ObjectMapper objectMapper;

	public GeminiAssistantService(AiAgentGeminiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public String createMessage(
			BudgetForecastRequest request,
			String healthStatus,
			long daysUntilRunOut,
			long billingDateGapDays
	) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			return fallbackMessage(healthStatus, daysUntilRunOut, billingDateGapDays);
		}

		try {
			String prompt = """
					다음 예산 분석 결과를 한국어 한 문장으로 요약해줘.
					- 상태: %s
					- 예산 소진까지 남은 일수: %d일
					- 결제일과의 차이(결제일-소진일): %d일
					- 현재 사용액: %s USD / 월예산: %s USD
					""".formatted(
					healthStatus,
					daysUntilRunOut,
					billingDateGapDays,
					request.currentSpendUsd(),
					request.monthlyBudgetUsd()
			);

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

			String response = RestClient.create()
					.post()
					.uri(uri)
					.body(body)
					.retrieve()
					.body(String.class);

			if (response == null || response.isBlank()) {
				return fallbackMessage(healthStatus, daysUntilRunOut, billingDateGapDays);
			}
			JsonNode root = objectMapper.readTree(response);
			JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			if (textNode.isMissingNode() || textNode.asText().isBlank()) {
				return fallbackMessage(healthStatus, daysUntilRunOut, billingDateGapDays);
			}
			return textNode.asText().trim();
		} catch (Exception ignored) {
			return fallbackMessage(healthStatus, daysUntilRunOut, billingDateGapDays);
		}
	}

	private static String fallbackMessage(String healthStatus, long daysUntilRunOut, long billingDateGapDays) {
		return "현재 상태는 " + healthStatus + "이며, 현재 추세라면 약 " + daysUntilRunOut
				+ "일 뒤 예산 또는 토큰이 소진될 수 있습니다. 결제일과의 차이는 " + billingDateGapDays + "일입니다.";
	}
}
