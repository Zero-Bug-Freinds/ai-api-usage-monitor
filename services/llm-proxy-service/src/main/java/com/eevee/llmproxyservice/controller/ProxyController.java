package com.eevee.llmproxyservice.controller;

import com.eevee.llmproxyservice.service.GeminiProviderImpl;
import com.eevee.llmproxyservice.service.OpenAiProviderImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class ProxyController {

    private final OpenAiProviderImpl openAiProvider;
    private final GeminiProviderImpl geminiProvider;

    @PostMapping("/**")
    public ResponseEntity<String> forward(
            @RequestBody String payload,
            @RequestHeader(value = "X-Client-Api-Key", required = false) String apiKey,
            HttpServletRequest request
    ) {
        String requestUri = request.getRequestURI();
        String uriPath = requestUri.startsWith("/proxy") ? requestUri.substring("/proxy".length()) : requestUri;
        String normalizedPath = uriPath.isBlank() ? "/" : uriPath;
        String routeKey = normalizedPath.toLowerCase();
        String clientKey = apiKey == null || apiKey.isBlank() ? "anonymous" : apiKey;

        if (routeKey.contains("openai") || routeKey.contains("chat/completions")) {
            return openAiProvider.forwardAndLog(payload, clientKey, normalizedPath);
        }

        if (routeKey.contains("gemini") || routeKey.contains("models")) {
            return geminiProvider.forwardAndLog(payload, clientKey, normalizedPath);
        }

        return ResponseEntity.badRequest().body("Unsupported provider path: " + normalizedPath);
    }
}
