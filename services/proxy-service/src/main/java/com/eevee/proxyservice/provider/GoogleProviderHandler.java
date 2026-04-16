package com.eevee.proxyservice.provider;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

@Component
public class GoogleProviderHandler implements ProviderHandler {

    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public GoogleProviderHandler(ObjectMapper objectMapper, com.eevee.proxyservice.config.ProxyProperties properties) {
        this.objectMapper = objectMapper;
        var ep = properties.getProviders().get("google");
        this.baseUrl = ep != null ? ep.getBaseUrl() : "https://generativelanguage.googleapis.com";
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GOOGLE;
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public void applyUpstreamAuth(HttpHeaders outgoing, String apiKey) {
        // Key is applied as query parameter in buildUpstreamUri; strip any client-supplied key headers.
    }

    @Override
    public URI buildUpstreamUri(String baseUrl, String remainderPath, String rawQuery, String apiKey) {
        String path = remainderPath.startsWith("/") ? remainderPath : "/" + remainderPath;
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(baseUrl + path);
        if (rawQuery != null && !rawQuery.isBlank()) {
            b.query(rawQuery);
        }
        b.replaceQueryParam("key", apiKey);
        return b.build(true).toUri();
    }

    @Override
    public TokenUsage parseUsageFromResponseJson(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode meta = root.get("usageMetadata");
            if (meta == null || meta.isNull()) {
                return null;
            }
            String model = firstNonBlank(text(root.get("modelVersion")), text(root.get("model")));
            Long prompt = longVal(meta.get("promptTokenCount"));
            Long completion = longVal(meta.get("candidatesTokenCount"));
            Long total = longVal(meta.get("totalTokenCount"));
            return new TokenUsage(model, prompt, completion, total,
                    null, null, null, null, null, null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
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
        h.add("x-goog-api-key", "");
        h.add("X-Goog-Api-Key", "");
        return h;
    }
}
