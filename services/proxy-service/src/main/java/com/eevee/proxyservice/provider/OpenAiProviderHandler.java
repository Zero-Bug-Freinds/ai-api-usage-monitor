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
        TokenUsage merged = null;
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
                    merged = mergeTokenUsage(merged, u);
                }
            } catch (IOException ignored) {
                // skip non-json lines
            }
        }
        return merged;
    }

    /**
     * Later SSE chunks may repeat usage with only {@code total_tokens}, which would erase
     * input/output and breakdown from an earlier chunk if we took the last chunk only.
     * Non-null fields from {@code next} win; nulls keep {@code previous}.
     */
    static TokenUsage mergeTokenUsage(TokenUsage previous, TokenUsage next) {
        if (next == null) {
            return previous;
        }
        if (previous == null) {
            return next;
        }
        return new TokenUsage(
                coalesce(next.model(), previous.model()),
                coalesce(next.promptTokens(), previous.promptTokens()),
                coalesce(next.completionTokens(), previous.completionTokens()),
                coalesce(next.totalTokens(), previous.totalTokens()),
                coalesce(next.promptCachedTokens(), previous.promptCachedTokens()),
                coalesce(next.promptAudioTokens(), previous.promptAudioTokens()),
                coalesce(next.completionReasoningTokens(), previous.completionReasoningTokens()),
                coalesce(next.completionAudioTokens(), previous.completionAudioTokens()),
                coalesce(next.completionAcceptedPredictionTokens(), previous.completionAcceptedPredictionTokens()),
                coalesce(next.completionRejectedPredictionTokens(), previous.completionRejectedPredictionTokens())
        );
    }

    private static <T> T coalesce(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }

    private TokenUsage extractUsage(JsonNode root) {
        JsonNode usage = root.get("usage");
        if (usage == null || usage.isNull()) {
            return null;
        }
        String model = text(root.get("model"));
        Long prompt = firstLong(usage.get("prompt_tokens"), usage.get("input_tokens"));
        Long completion = firstLong(usage.get("completion_tokens"), usage.get("output_tokens"));
        Long total = longVal(usage.get("total_tokens"));
        if (total == null && prompt != null && completion != null) {
            total = prompt + completion;
        }
        // Chat Completions: `prompt_tokens_details` / `completion_tokens_details`.
        // Responses API: `input_tokens_details` / `output_tokens_details` (same inner keys).
        JsonNode promptDetails = firstNode(
                usage.get("prompt_tokens_details"),
                usage.get("input_tokens_details")
        );
        Long promptCachedTokens = longVal(promptDetails != null ? promptDetails.get("cached_tokens") : null);
        Long promptAudioTokens = longVal(promptDetails != null ? promptDetails.get("audio_tokens") : null);

        JsonNode completionDetails = firstNode(
                usage.get("completion_tokens_details"),
                usage.get("output_tokens_details")
        );
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

    private static Long firstLong(JsonNode primary, JsonNode fallback) {
        Long v = longVal(primary);
        return v != null ? v : longVal(fallback);
    }

    private static JsonNode firstNode(JsonNode primary, JsonNode fallback) {
        if (primary != null && !primary.isNull()) {
            return primary;
        }
        return fallback;
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
