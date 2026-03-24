package com.eevee.proxyservice.provider;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AnthropicProviderHandler implements ProviderHandler {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AnthropicProviderHandler(ObjectMapper objectMapper, com.eevee.proxyservice.config.ProxyProperties properties) {
        this.objectMapper = objectMapper;
        var ep = properties.getProviders().get("anthropic");
        this.baseUrl = ep != null ? ep.getBaseUrl() : "https://api.anthropic.com";
    }

    @Override
    public AiProvider provider() {
        return AiProvider.ANTHROPIC;
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public void applyUpstreamAuth(HttpHeaders outgoing, String apiKey) {
        outgoing.set("x-api-key", apiKey);
        outgoing.set("anthropic-version", ANTHROPIC_VERSION);
    }

    @Override
    public TokenUsage parseUsageFromResponseJson(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode usage = root.get("usage");
            if (usage == null || usage.isNull()) {
                return null;
            }
            String model = text(root.get("model"));
            Long input = longVal(usage.get("input_tokens"));
            Long output = longVal(usage.get("output_tokens"));
            Long total = null;
            if (input != null && output != null) {
                total = input + output;
            }
            return new TokenUsage(model, input, output, total);
        } catch (IOException e) {
            return null;
        }
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
        h.add("x-api-key", "");
        return h;
    }
}
