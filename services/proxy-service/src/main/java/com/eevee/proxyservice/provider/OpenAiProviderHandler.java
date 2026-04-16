package com.eevee.proxyservice.provider;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OpenAiProviderHandler implements ProviderHandler {

    private static final Pattern SSE_DATA_LINE = Pattern.compile("^data:\\s*(.+)$", Pattern.MULTILINE);

    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OpenAiProviderHandler(ObjectMapper objectMapper, com.eevee.proxyservice.config.ProxyProperties properties) {
        this.objectMapper = objectMapper;
        var ep = properties.getProviders().get("openai");
        this.baseUrl = ep != null ? ep.getBaseUrl() : "https://api.openai.com";
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OPENAI;
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public void applyUpstreamAuth(HttpHeaders outgoing, String apiKey) {
        outgoing.setBearerAuth(apiKey);
    }

    @Override
    public TokenUsage parseUsageFromResponseJson(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return extractUsage(root);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public TokenUsage parseUsageFromSse(String accumulatedSse) {
        TokenUsage last = null;
        Matcher m = SSE_DATA_LINE.matcher(accumulatedSse);
        while (m.find()) {
            String payload = m.group(1).trim();
            if ("[DONE]".equals(payload)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(payload);
                TokenUsage u = extractUsage(root);
                if (u != null) {
                    last = u;
                }
            } catch (IOException ignored) {
                // skip non-json lines
            }
        }
        return last;
    }

    private TokenUsage extractUsage(JsonNode root) {
        JsonNode usage = root.get("usage");
        if (usage == null || usage.isNull()) {
            return null;
        }
        String model = text(root.get("model"));
        Long prompt = longVal(usage.get("prompt_tokens"));
        Long completion = longVal(usage.get("completion_tokens"));
        Long total = longVal(usage.get("total_tokens"));
        // OpenAI provides nested token details for prompt/completion.
        // For non-streaming JSON this comes from `usage.prompt_tokens_details` and
        // `usage.completion_tokens_details`.
        JsonNode promptDetails = usage.get("prompt_tokens_details");
        Long promptCachedTokens = longVal(promptDetails != null ? promptDetails.get("cached_tokens") : null);
        Long promptAudioTokens = longVal(promptDetails != null ? promptDetails.get("audio_tokens") : null);

        JsonNode completionDetails = usage.get("completion_tokens_details");
        Long completionReasoningTokens = longVal(completionDetails != null ? completionDetails.get("reasoning_tokens") : null);
        Long completionAudioTokens = longVal(completionDetails != null ? completionDetails.get("audio_tokens") : null);
        Long completionAcceptedPredictionTokens = longVal(
                completionDetails != null ? completionDetails.get("accepted_prediction_tokens") : null
        );
        Long completionRejectedPredictionTokens = longVal(
                completionDetails != null ? completionDetails.get("rejected_prediction_tokens") : null
        );
        if (prompt == null && completion == null && total == null) {
            return null;
        }
        return new TokenUsage(
                model,
                prompt,
                completion,
                total,
                promptCachedTokens,
                promptAudioTokens,
                completionReasoningTokens,
                completionAudioTokens,
                completionAcceptedPredictionTokens,
                completionRejectedPredictionTokens
        );
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }

    private static Long longVal(JsonNode n) {
        return n == null || n.isNull() ? null : n.asLong();
    }

    @Override
    public HttpHeaders blockedIncomingHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.AUTHORIZATION, "");
        h.add("api-key", "");
        h.add("OpenAI-Organization", "");
        return h;
    }
}
