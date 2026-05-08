package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExternalModelCatalogService {

	private static final Logger log = LoggerFactory.getLogger(ExternalModelCatalogService.class);
	private static final BigDecimal MAX_PRICE_JUMP_MULTIPLIER = new BigDecimal("10");
	private static final BigDecimal MIN_PRICE_DROP_DIVISOR = new BigDecimal("10");

	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	@Value("${ai-agent.recommendation.catalog.url:}")
	private String catalogUrl;

	@Value("${ai-agent.recommendation.catalog.request-timeout-ms:5000}")
	private long requestTimeoutMs;

	@Value("${ai-agent.recommendation.catalog.api-key:}")
	private String catalogApiKey;

	@Value("${ai-agent.recommendation.catalog.openrouter.referer:}")
	private String openRouterReferer;

	@Value("${ai-agent.recommendation.catalog.openrouter.title:}")
	private String openRouterTitle;

	private volatile CatalogSnapshot snapshot = new CatalogSnapshot(
			defaultModels(),
			"default",
			Instant.now(),
			Instant.now(),
			true,
			null
	);

	public ExternalModelCatalogService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.build();
	}

	@PostConstruct
	public void init() {
		refreshCatalog();
	}

	@Scheduled(fixedDelayString = "${ai-agent.recommendation.catalog.refresh-ms:300000}")
	public void scheduledRefresh() {
		refreshCatalog();
	}

	public CatalogSnapshot currentCatalog() {
		return snapshot;
	}

	private void refreshCatalog() {
		String trimmedUrl = catalogUrl != null ? catalogUrl.trim() : "";
		if (trimmedUrl.isEmpty()) {
			return;
		}
		try {
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(trimmedUrl))
					.GET()
					.timeout(Duration.ofMillis(requestTimeoutMs));
			if (catalogApiKey != null && !catalogApiKey.isBlank()) {
				requestBuilder.header("Authorization", "Bearer " + catalogApiKey.trim());
			}
			if (openRouterReferer != null && !openRouterReferer.isBlank()) {
				requestBuilder.header("HTTP-Referer", openRouterReferer.trim());
			}
			if (openRouterTitle != null && !openRouterTitle.isBlank()) {
				requestBuilder.header("X-Title", openRouterTitle.trim());
			}

			HttpRequest request = requestBuilder.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn("Model catalog fetch failed. status={}", response.statusCode());
				markRefreshFailure("HTTP " + response.statusCode());
				return;
			}
			List<ModelPricing> parsed = parseCatalog(response.body());
			if (parsed.isEmpty()) {
				log.warn("Model catalog fetch returned empty payload. keep previous snapshot.");
				markRefreshFailure("EMPTY_PAYLOAD");
				return;
			}
			List<ModelPricing> validated = validateWithPreviousSnapshot(parsed, snapshot.models());
			if (validated.isEmpty()) {
				log.warn("Model catalog validation rejected all fetched rows. keep previous snapshot.");
				markRefreshFailure("VALIDATION_REJECTED");
				return;
			}
			Instant now = Instant.now();
			snapshot = new CatalogSnapshot(validated, trimmedUrl, now, now, true, null);
		} catch (Exception ex) {
			log.warn("Failed to refresh model catalog from external source. keep previous snapshot.", ex);
			markRefreshFailure(ex.getClass().getSimpleName());
		}
	}

	private void markRefreshFailure(String reason) {
		CatalogSnapshot current = snapshot;
		snapshot = new CatalogSnapshot(
				current.models(),
				current.source(),
				current.updatedAt(),
				Instant.now(),
				false,
				reason
		);
	}

	private List<ModelPricing> parseCatalog(String body) throws Exception {
		JsonNode root = objectMapper.readTree(body);
		if (root.has("data") && root.get("data").isArray()) {
			return parseOpenRouterData(root.get("data"));
		}
		JsonNode modelArray = root.isArray() ? root : root.path("models");
		if (modelArray == null || !modelArray.isArray()) {
			return List.of();
		}
		List<ModelPricing> result = new ArrayList<>();
		for (JsonNode node : modelArray) {
			String modelName = firstText(node, "modelName", "model", "id");
			if (modelName == null || modelName.isBlank()) {
				continue;
			}
			String provider = firstText(node, "provider");
			BigDecimal inputPrice = firstDecimal(node, "inputPricePer1mUsd", "inputPricePer1m", "inputPrice");
			BigDecimal outputPrice = firstDecimal(node, "outputPricePer1mUsd", "outputPricePer1m", "outputPrice");
			if (inputPrice == null || outputPrice == null || inputPrice.signum() < 0 || outputPrice.signum() < 0) {
				continue;
			}
			Integer contextWindow = firstInteger(node, "contextWindow", "maxContextWindow");
			String status = firstText(node, "status");
			String keyFeature = firstText(node, "keyFeature", "description", "note");
			if (keyFeature == null || keyFeature.isBlank()) {
				keyFeature = "외부 카탈로그 기준 단가 적용";
			}
			result.add(new ModelPricing(
					provider != null ? provider.trim() : null,
					modelName.trim(),
					inputPrice,
					outputPrice,
					keyFeature.trim(),
					contextWindow,
					status != null ? status.trim() : null
			));
		}
		Map<String, ModelPricing> deduplicated = result.stream().collect(Collectors.toMap(
				item -> item.modelName().toLowerCase(Locale.ROOT),
				Function.identity(),
				(existing, replacement) -> replacement
		));
		return deduplicated.values().stream()
				.sorted(Comparator.comparing(ModelPricing::modelName))
				.toList();
	}

	private List<ModelPricing> parseOpenRouterData(JsonNode dataArray) {
		List<ModelPricing> result = new ArrayList<>();
		for (JsonNode node : dataArray) {
			String modelName = firstText(node, "id", "name");
			if (modelName == null || modelName.isBlank()) {
				continue;
			}
			JsonNode pricing = node.path("pricing");
			if (pricing.isMissingNode() || pricing.isNull()) {
				continue;
			}
			BigDecimal promptPerTokenUsd = firstDecimal(pricing, "prompt", "input");
			BigDecimal completionPerTokenUsd = firstDecimal(pricing, "completion", "output");
			if (promptPerTokenUsd == null || completionPerTokenUsd == null) {
				continue;
			}
			BigDecimal inputPer1mUsd = promptPerTokenUsd.multiply(BigDecimal.valueOf(1_000_000L)).setScale(6, RoundingMode.HALF_UP);
			BigDecimal outputPer1mUsd = completionPerTokenUsd.multiply(BigDecimal.valueOf(1_000_000L)).setScale(6, RoundingMode.HALF_UP);

			Integer contextWindow = null;
			JsonNode topProvider = node.path("top_provider");
			if (topProvider.isObject()) {
				contextWindow = firstInteger(topProvider, "context_length", "max_context_length");
			}

			result.add(new ModelPricing(
					extractProviderFromModelId(modelName),
					modelName.trim(),
					inputPer1mUsd,
					outputPer1mUsd,
					"OpenRouter 실시간 카탈로그 기반",
					contextWindow,
					"ACTIVE"
			));
		}
		Map<String, ModelPricing> deduplicated = result.stream().collect(Collectors.toMap(
				item -> item.modelName().toLowerCase(Locale.ROOT),
				Function.identity(),
				(existing, replacement) -> replacement
		));
		return deduplicated.values().stream()
				.sorted(Comparator.comparing(ModelPricing::modelName))
				.toList();
	}

	private static String extractProviderFromModelId(String modelId) {
		if (modelId == null || modelId.isBlank()) {
			return "UNKNOWN";
		}
		int slashIdx = modelId.indexOf('/');
		if (slashIdx <= 0) {
			return "UNKNOWN";
		}
		return modelId.substring(0, slashIdx).toUpperCase(Locale.ROOT);
	}

	private List<ModelPricing> validateWithPreviousSnapshot(
			List<ModelPricing> parsed,
			List<ModelPricing> previous
	) {
		Map<String, ModelPricing> previousByModel = previous.stream().collect(Collectors.toMap(
				item -> item.modelName().toLowerCase(Locale.ROOT),
				Function.identity(),
				(existing, ignored) -> existing
		));
		List<ModelPricing> accepted = new ArrayList<>();

		for (ModelPricing candidate : parsed) {
			if (!isActive(candidate.status())) {
				continue;
			}
			if (!isValidPrice(candidate.inputPricePer1mUsd()) || !isValidPrice(candidate.outputPricePer1mUsd())) {
				log.warn("Reject invalid model pricing row. model={}", candidate.modelName());
				continue;
			}
			ModelPricing previousModel = previousByModel.get(candidate.modelName().toLowerCase(Locale.ROOT));
			if (previousModel != null && isPriceJumpSuspicious(previousModel, candidate)) {
				log.warn("Reject suspicious price jump. model={}, previousInput={}, newInput={}, previousOutput={}, newOutput={}",
						candidate.modelName(),
						previousModel.inputPricePer1mUsd(),
						candidate.inputPricePer1mUsd(),
						previousModel.outputPricePer1mUsd(),
						candidate.outputPricePer1mUsd());
				accepted.add(previousModel);
				continue;
			}
			accepted.add(candidate);
		}

		Map<String, ModelPricing> acceptedByName = accepted.stream().collect(Collectors.toMap(
				item -> item.modelName().toLowerCase(Locale.ROOT),
				Function.identity(),
				(existing, replacement) -> replacement
		));
		for (ModelPricing previousModel : previous) {
			String key = previousModel.modelName().toLowerCase(Locale.ROOT);
			if (!acceptedByName.containsKey(key)) {
				acceptedByName.put(key, previousModel);
			}
		}
		return acceptedByName.values().stream()
				.sorted(Comparator.comparing(ModelPricing::modelName))
				.toList();
	}

	private static boolean isActive(String status) {
		if (status == null || status.isBlank()) {
			return true;
		}
		return "ACTIVE".equalsIgnoreCase(status.trim());
	}

	private static boolean isValidPrice(BigDecimal value) {
		return value != null && value.signum() >= 0;
	}

	private static boolean isPriceJumpSuspicious(ModelPricing previous, ModelPricing current) {
		return isSinglePriceJumpSuspicious(previous.inputPricePer1mUsd(), current.inputPricePer1mUsd())
				|| isSinglePriceJumpSuspicious(previous.outputPricePer1mUsd(), current.outputPricePer1mUsd());
	}

	private static boolean isSinglePriceJumpSuspicious(BigDecimal previous, BigDecimal current) {
		if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) <= 0) {
			return false;
		}
		BigDecimal upperBound = previous.multiply(MAX_PRICE_JUMP_MULTIPLIER);
		BigDecimal lowerBound = previous.divide(MIN_PRICE_DROP_DIVISOR, 6, RoundingMode.HALF_UP);
		return current.compareTo(upperBound) > 0 || current.compareTo(lowerBound) < 0;
	}

	private static String firstText(JsonNode node, String... fields) {
		for (String field : fields) {
			if (node.hasNonNull(field)) {
				String value = node.get(field).asText();
				if (value != null && !value.isBlank()) {
					return value;
				}
			}
		}
		return null;
	}

	private static BigDecimal firstDecimal(JsonNode node, String... fields) {
		for (String field : fields) {
			if (node.hasNonNull(field)) {
				try {
					return new BigDecimal(node.get(field).asText());
				} catch (NumberFormatException ignored) {
					// try next field
				}
			}
		}
		return null;
	}

	private static Integer firstInteger(JsonNode node, String... fields) {
		for (String field : fields) {
			if (node.hasNonNull(field)) {
				try {
					return Integer.valueOf(node.get(field).asText());
				} catch (NumberFormatException ignored) {
					// try next field
				}
			}
		}
		return null;
	}

	private static List<ModelPricing> defaultModels() {
		return List.of(
				new ModelPricing("GOOGLE", "gemini-2.5-flash", new BigDecimal("0.075"), new BigDecimal("0.30"), "입력 단가가 낮아 대량 입력 패턴에 유리", 1000000, "ACTIVE"),
				new ModelPricing("ANTHROPIC", "claude-3-haiku", new BigDecimal("0.25"), new BigDecimal("1.25"), "저비용/빠른 응답으로 일반 대화형에 적합", 200000, "ACTIVE"),
				new ModelPricing("OPENAI", "gpt-4o-mini", new BigDecimal("0.15"), new BigDecimal("0.60"), "균형형 품질/비용 모델", 128000, "ACTIVE"),
				new ModelPricing("GROQ", "llama-3-8b-groq", new BigDecimal("0.05"), new BigDecimal("0.08"), "초저지연/저비용 워크로드에 적합", 8192, "ACTIVE")
		);
	}

	public record CatalogSnapshot(
			List<ModelPricing> models,
			String source,
			Instant updatedAt,
			Instant lastAttemptAt,
			boolean lastRefreshSucceeded,
			String lastRefreshError
	) {
	}

	public record ModelPricing(
			String provider,
			String modelName,
			BigDecimal inputPricePer1mUsd,
			BigDecimal outputPricePer1mUsd,
			String keyFeature,
			Integer contextWindow,
			String status
	) {
	}
}
