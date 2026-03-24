package com.eevee.proxyservice.provider;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Maps a logical provider to upstream base URL, auth headers, and usage extraction.
 */
public interface ProviderHandler {

    AiProvider provider();

    String baseUrl();

    /**
     * Injects provider credentials. Must not log raw keys.
     */
    void applyUpstreamAuth(HttpHeaders outgoing, String apiKey);

    /**
     * Appends provider key to the upstream URI when required (e.g. Google query param).
     */
    default URI buildUpstreamUri(String baseUrl, String remainderPath, String rawQuery, String apiKey) {
        String path = remainderPath.startsWith("/") ? remainderPath : "/" + remainderPath;
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(baseUrl + path);
        if (rawQuery != null && !rawQuery.isBlank()) {
            b.query(rawQuery);
        }
        return b.build(true).toUri();
    }

    TokenUsage parseUsageFromResponseJson(String responseBody);

    /**
     * Best-effort parse from accumulated SSE text (e.g. OpenAI stream ending with usage).
     */
    default TokenUsage parseUsageFromSse(String accumulatedSse) {
        return null;
    }

    /**
     * Headers that must never be forwarded from client (user-supplied provider keys).
     */
    HttpHeaders blockedIncomingHeaders();
}
