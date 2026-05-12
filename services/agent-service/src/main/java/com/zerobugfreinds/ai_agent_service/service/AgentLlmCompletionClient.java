package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zerobugfreinds.ai_agent_service.config.AiAgentDeepseekHttpClientConfiguration;
import com.zerobugfreinds.ai_agent_service.config.AiAgentDeepseekProperties;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiHttpClientConfiguration;
import com.zerobugfreinds.ai_agent_service.config.AiAgentGeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tries DeepSeek first (OpenAI-compatible chat completions), then Gemini when DeepSeek
 * fails or returns no usable candidate. Responses are shaped like Gemini {@code generateContent} JSON
 * so existing parsers stay unchanged.
 */
@Component
public class AgentLlmCompletionClient {

	private static final Logger log = LoggerFactory.getLogger(AgentLlmCompletionClient.class);
	private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";

	private final AiAgentGeminiProperties geminiProperties;
	private final AiAgentDeepseekProperties deepseekProperties;
	private final ObjectMapper objectMapper;
	private final RestClient geminiRestClient;
	private final RestClient deepseekRestClient;

	public AgentLlmCompletionClient(
			AiAgentGeminiProperties geminiProperties,
			AiAgentDeepseekProperties deepseekProperties,
			ObjectMapper objectMapper,
			@Qualifier(AiAgentGeminiHttpClientConfiguration.GEMINI_REST_CLIENT) RestClient geminiRestClient,
			@Qualifier(AiAgentDeepseekHttpClientConfiguration.DEEPSEEK_REST_CLIENT) RestClient deepseekRestClient
	) {
		this.geminiProperties = geminiProperties;
		this.deepseekProperties = deepseekProperties;
		this.objectMapper = objectMapper;
		this.geminiRestClient = geminiRestClient;
		this.deepseekRestClient = deepseekRestClient;
	}

	public boolean geminiConfigured() {
		return geminiProperties.apiKey() != null && !geminiProperties.apiKey().isBlank();
	}

	public boolean deepseekConfigured() {
		return deepseekProperties.apiKey() != null && !deepseekProperties.apiKey().isBlank();
	}

	/**
	 * @return Gemini-shaped JSON string, or {@code null} when no provider succeeds
	 */
	public String completeAsGeminiGenerateContentJson(String prompt, double temperature) {
		String deepseekShaped = null;
		if (deepseekConfigured()) {
			deepseekShaped = callDeepseekAsGeminiShaped(prompt, temperature);
			if (deepseekShaped != null && hasNonBlankTextCandidate(deepseekShaped)) {
				log.info("DeepSeek primary produced a usable response");
				return deepseekShaped;
			}
			if (deepseekShaped != null) {
				log.info("DeepSeek returned no usable text candidate; trying Gemini fallback");
			} else {
				log.info("DeepSeek call failed or empty; trying Gemini fallback");
			}
		}

		String geminiResponse = tryGemini(prompt, temperature);
		if (geminiResponse != null && hasNonBlankTextCandidate(geminiResponse)) {
			log.info("Gemini fallback produced a usable response");
			return geminiResponse;
		}

		if (!deepseekConfigured() && !geminiConfigured()) {
			log.info("LLM skipped: set AI_AGENT_DEEPSEEK_API_KEY and/or AI_AGENT_GEMINI_API_KEY (or GOOGLE_API_KEY chain for Gemini)");
		} else if (deepseekConfigured() && !geminiConfigured()) {
			log.info("Gemini fallback skipped: Gemini API key not configured");
		}

		return geminiResponse != null ? geminiResponse : deepseekShaped;
	}

	private String tryGemini(String prompt, double temperature) {
		if (!geminiConfigured()) {
			return null;
		}
		Map<String, Object> body = Map.of(
				"contents", new Object[] {
						Map.of("parts", new Object[] {Map.of("text", prompt)})
				},
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"temperature", temperature
				)
		);
		String configuredModel = (geminiProperties.model() == null || geminiProperties.model().isBlank())
				? DEFAULT_GEMINI_MODEL
				: geminiProperties.model().trim();
		String baseUrl = (geminiProperties.baseUrl() == null || geminiProperties.baseUrl().isBlank())
				? "https://generativelanguage.googleapis.com"
				: geminiProperties.baseUrl();
		String uri = baseUrl + "/v1beta/models/" + configuredModel + ":generateContent?key=" + geminiProperties.apiKey();
		try {
			return geminiRestClient.post()
					.uri(uri)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
		} catch (RestClientResponseException ex) {
			log.warn(
					"Gemini call failed. model={}, status={}, responseBody={}",
					configuredModel,
					ex.getStatusCode(),
					summarizeErrorBody(ex.getResponseBodyAsString())
			);
			return null;
		} catch (Exception ex) {
			log.warn("Gemini call failed: {}", ex.getMessage());
			return null;
		}
	}

	private String callDeepseekAsGeminiShaped(String prompt, double temperature) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", deepseekProperties.resolvedModel());
		body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
		body.put("temperature", temperature);
		body.put("response_format", Map.of("type", "json_object"));
		String url = deepseekProperties.resolvedBaseUrl() + "/v1/chat/completions";
		String model = deepseekProperties.resolvedModel();
		log.info(
				"[DEEPSEEK] HTTP POST {} model={} promptChars={} temperature={}",
				url,
				model,
				prompt.length(),
				temperature
		);
		try {
			String raw = deepseekRestClient.post()
					.uri(url)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + deepseekProperties.apiKey().trim())
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
			if (raw == null || raw.isBlank()) {
				return null;
			}
			JsonNode root = objectMapper.readTree(raw);
			JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
			if (contentNode.isMissingNode() || contentNode.asText("").isBlank()) {
				log.warn("DeepSeek response missing assistant content: summary={}", summarizeResponseBody(raw));
				return null;
			}
			String assistantText = contentNode.asText();
			return toGeminiShapedResponse(assistantText, root.path("usage"));
		} catch (RestClientResponseException ex) {
			log.warn(
					"DeepSeek call failed. status={}, responseBody={}",
					ex.getStatusCode(),
					summarizeErrorBody(ex.getResponseBodyAsString())
			);
			return null;
		} catch (Exception ex) {
			log.warn("DeepSeek response parse or wrap failed: {}", ex.getMessage());
			return null;
		}
	}

	private String toGeminiShapedResponse(String assistantContent, JsonNode usage) throws Exception {
		ObjectNode out = objectMapper.createObjectNode();
		ArrayNode candidates = out.putArray("candidates");
		ObjectNode candidate = candidates.addObject();
		ObjectNode content = candidate.putObject("content");
		ArrayNode parts = content.putArray("parts");
		ObjectNode part = parts.addObject();
		part.put("text", assistantContent);
		ObjectNode usageMetadata = out.putObject("usageMetadata");
		usageMetadata.put("promptTokenCount", usage.path("prompt_tokens").asInt(-1));
		usageMetadata.put("candidatesTokenCount", usage.path("completion_tokens").asInt(-1));
		usageMetadata.put("totalTokenCount", usage.path("total_tokens").asInt(-1));
		return objectMapper.writeValueAsString(out);
	}

	private boolean hasNonBlankTextCandidate(String geminiStyleBody) {
		try {
			JsonNode root = objectMapper.readTree(geminiStyleBody);
			JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			return !textNode.isMissingNode() && !textNode.asText("").isBlank();
		} catch (Exception ex) {
			return false;
		}
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
