package com.zerobugfreinds.team_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class IdentityApiKeyLookupClient {
    private final HttpClient httpClient;
    private final List<String> identityServiceBaseUrls;

    public IdentityApiKeyLookupClient(
            @Value("${identity.service.url:http://host.docker.internal:8090}") String identityServiceBaseUrl
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.identityServiceBaseUrls = resolveCandidateBaseUrls(identityServiceBaseUrl);
    }

    public boolean existsByHashedKey(String provider, String hashedKey) {
        if (provider == null || provider.isBlank() || hashedKey == null || hashedKey.isBlank()) {
            throw new IllegalArgumentException("provider와 hashedKey는 필수입니다");
        }

        String normalizedProvider = provider.trim();
        String normalizedHash = hashedKey.trim();
        for (String baseUrl : identityServiceBaseUrls) {
            URI uri = UriComponentsBuilder
                    .fromUriString(baseUrl + "/internal/v1/api-keys/lookup")
                    .queryParam("provider", normalizedProvider)
                    .queryParam("hashedKey", normalizedHash)
                    .build(true)
                    .toUri();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .header("Accept", "application/json")
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

    private static List<String> resolveCandidateBaseUrls(String configuredBaseUrl) {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, configuredBaseUrl);
        if (configuredBaseUrl != null && configuredBaseUrl.contains("host.docker.internal")) {
            addCandidate(candidates, "http://localhost:8090");
            addCandidate(candidates, "http://identity-service:8090");
        }
        if (candidates.isEmpty()) {
            addCandidate(candidates, "http://host.docker.internal:8090");
            addCandidate(candidates, "http://localhost:8090");
            addCandidate(candidates, "http://identity-service:8090");
        }
        return List.copyOf(candidates);
    }

    private static void addCandidate(Set<String> candidates, String baseUrl) {
        if (baseUrl == null) {
            return;
        }
        String normalized = baseUrl.trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        candidates.add(normalized);
    }
}
