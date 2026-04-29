package com.eevee.llmproxyservice.service;

import com.eevee.llmproxyservice.domain.UsageLog;
import com.eevee.llmproxyservice.repository.UsageLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class OpenAiProviderImpl implements LlmProviderService {

    private static final String OPENAI_BASE_URL = "https://api.openai.com";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final UsageLogRepository usageLogRepository;

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Override
    public ResponseEntity<String> forwardAndLog(String payload, String apiKey, String uriPath) {
        String normalizedPath = uriPath.startsWith("/") ? uriPath : "/" + uriPath;
        String targetUrl = OPENAI_BASE_URL + normalizedPath;

        ResponseEntity<String> response = webClientBuilder.build()
                .post()
                .uri(targetUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(payload)
                .exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> ResponseEntity.status(clientResponse.statusCode()).body(body)))
                .block();

        ResponseEntity<String> safeResponse = response == null ? ResponseEntity.internalServerError().body("") : response;
        saveUsageLog(apiKey, normalizedPath, safeResponse.getBody());
        return safeResponse;
    }

    private void saveUsageLog(String apiKey, String modelName, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
            JsonNode usage = root.path("usage");

            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);

            UsageLog usageLog = new UsageLog();
            usageLog.setApiKey(apiKey);
            usageLog.setModelName(modelName);
            usageLog.setPromptTokens(promptTokens);
            usageLog.setCompletionTokens(completionTokens);
            usageLog.setTotalTokens(totalTokens);
            usageLogRepository.save(usageLog);
        } catch (Exception ignored) {
            // Upstream payload structure can vary by endpoint; skip persistence when usage parsing fails.
        }
    }
}
