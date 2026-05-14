package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.team_service.config.IdentityServiceUrlSupport;
import com.zerobugfreinds.team_service.dto.InternalFingerprintLookupRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class IdentityApiKeyLookupClient {
    private final HttpClient httpClient;
    private final List<String> identityServiceBaseUrls;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;
    private final String fingerprintLookupToken;

    public IdentityApiKeyLookupClient(
            @Qualifier("identityServiceHttpClient") HttpClient httpClient,
            IdentityServiceUrlSupport identityServiceUrlSupport,
            ObjectMapper objectMapper,
            @Value("${identity.http.read-timeout-ms:5000}") int readTimeoutMs,
            @Value("${api.internal.key-lookup-token:}") String fingerprintLookupToken
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofMillis(Math.max(1, readTimeoutMs));
        this.identityServiceBaseUrls = identityServiceUrlSupport.apiKeyLookupBaseUrls();
        this.fingerprintLookupToken = fingerprintLookupToken != null ? fingerprintLookupToken.trim() : "";
    }

    public boolean existsByRawKeyFingerprint(String provider, String fingerprint) {
        if (provider == null || provider.isBlank() || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("provider와 fingerprint는 필수입니다");
        }
        if (fingerprintLookupToken.isBlank()) {
            throw new IllegalStateException(
                    "내부 API 키 조회 토큰이 설정되지 않았습니다 (api.internal.key-lookup-token / INTERNAL_API_KEY_LOOKUP_TOKEN)");
        }

        String normalizedProvider = provider.trim();
        String normalizedFingerprint = fingerprint.trim();
        InternalFingerprintLookupRequest payload =
                new InternalFingerprintLookupRequest(normalizedFingerprint, normalizedProvider);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("identity fingerprint lookup body 직렬화 실패", e);
        }

        for (String baseUrl : identityServiceBaseUrls) {
            URI uri = URI.create(baseUrl + "/internal/v1/api-keys/lookup");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + fingerprintLookupToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode == 404) {
                    return false;
                }
                if (statusCode == 200 || statusCode == 409) {
                    return true;
                }
                throw new IllegalStateException(
                        "identity-service 내부 조회 호출 실패 status=" + statusCode + " baseUrl=" + baseUrl
                );
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("identity-service 내부 조회 호출이 중단되었습니다", ex);
            } catch (IOException ex) {
                // 다음 후보 URL 로 재시도한다.
            }
        }
        throw new IllegalStateException("identity-service에 연결할 수 없어 개인 API 키 중복 검증에 실패했습니다");
    }
}
