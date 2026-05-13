package com.zerobugfreinds.team_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * identity-service 베이스 URL: 사용자 조회용 단일 URL과 API 키 lookup용(개발 폴백 포함) 목록을 공유한다.
 */
@Component
public class IdentityServiceUrlSupport {

    private final String primaryBaseUrl;
    private final List<String> apiKeyLookupBaseUrls;

    public IdentityServiceUrlSupport(
            @Value("${identity.service.url:http://host.docker.internal:8090}") String identityServiceUrl,
            @Value("${identity.service.lookup.fallback-urls-enabled:true}") boolean fallbackUrlsEnabled
    ) {
        this.primaryBaseUrl = identityServiceUrl == null ? "" : identityServiceUrl.replaceAll("/+$", "");
        this.apiKeyLookupBaseUrls = fallbackUrlsEnabled
                ? resolveCandidateBaseUrls(identityServiceUrl)
                : resolveSingleBaseUrl(identityServiceUrl);
    }

    /**
     * 사용자 조회 등 단일 호스트 — 설정값 그대로(끝 슬래시만 제거).
     */
    public String primaryBaseUrl() {
        return primaryBaseUrl;
    }

    /**
     * 팀 API 키 vs identity 개인 키 중복 검사: 연결 실패 시 개발용 후보 URL 순회에 사용.
     */
    public List<String> apiKeyLookupBaseUrls() {
        return apiKeyLookupBaseUrls;
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

    private static List<String> resolveSingleBaseUrl(String configuredBaseUrl) {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, configuredBaseUrl);
        if (candidates.isEmpty()) {
            addCandidate(candidates, "http://host.docker.internal:8090");
        }
        return List.of(candidates.iterator().next());
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
