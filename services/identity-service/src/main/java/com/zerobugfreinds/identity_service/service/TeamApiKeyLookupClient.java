package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.dto.InternalFingerprintLookupRequest;
import com.zerobugfreinds.identity_service.exception.TeamApiKeyLookupUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class TeamApiKeyLookupClient {
    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyLookupClient.class);

    private static final String DOCKER_TEAM_SERVICE_BASE_URL = "http://team-service:8093";
    private static final String LOCALHOST_TEAM_SERVICE_BASE_URL = "http://localhost:8093";
    private static final String HOST_DOCKER_INTERNAL_TEAM_SERVICE_BASE_URL = "http://host.docker.internal:8093";

    private final List<RestClient> restClients;
    private final List<String> baseUrls;
    private final String fingerprintLookupToken;

    public TeamApiKeyLookupClient(
            RestClient.Builder restClientBuilder,
            @Value("${identity.team-service.internal-base-url:${TEAM_SERVICE_INTERNAL_URL:http://team-service:8093}}")
            String teamServiceBaseUrl,
            @Value("${api.internal.key-lookup-token:}") String fingerprintLookupToken
    ) {
        this.baseUrls = resolveCandidateBaseUrls(teamServiceBaseUrl);
        this.restClients = this.baseUrls.stream()
                .map(baseUrl -> restClientBuilder.baseUrl(baseUrl).build())
                .toList();
        this.fingerprintLookupToken = fingerprintLookupToken != null ? fingerprintLookupToken.trim() : "";
        log.info("team api key lookup baseUrls={}", this.baseUrls);
    }

    /**
     * 팀 쪽에 동일 (provider, raw-key SHA-256 fingerprint) 등록이 있는지 POST 역조회로 확인한다.
     */
    public boolean existsByRawKeyFingerprint(String provider, String fingerprint) {
        if (provider == null || provider.isBlank() || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("provider와 fingerprint는 필수입니다");
        }
        if (fingerprintLookupToken.isBlank()) {
            throw new TeamApiKeyLookupUnavailableException(
                    "내부 API 키 조회 토큰이 설정되지 않았습니다 (api.internal.key-lookup-token / INTERNAL_API_KEY_LOOKUP_TOKEN)");
        }

        String normalizedProvider = provider.trim();
        String normalizedFingerprint = fingerprint.trim();
        InternalFingerprintLookupRequest body = new InternalFingerprintLookupRequest(
                normalizedFingerprint,
                normalizedProvider
        );
        for (int idx = 0; idx < restClients.size(); idx++) {
            RestClient client = restClients.get(idx);
            String baseUrl = baseUrls.get(idx);
            try {
                client.post()
                        .uri("/internal/v1/api-keys/lookup")
                        .header("Authorization", "Bearer " + fingerprintLookupToken)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (HttpClientErrorException.NotFound ex) {
                return false;
            } catch (HttpClientErrorException.Conflict ex) {
                return true;
            } catch (HttpClientErrorException ex) {
                throw new TeamApiKeyLookupUnavailableException(
                        "team-service 내부 조회 호출 실패 status=" + ex.getStatusCode() + " baseUrl=" + baseUrl,
                        ex
                );
            } catch (RuntimeException ex) {
                if (!isRecoverableConnectivityError(ex)) {
                    throw ex;
                }
                log.warn(
                        "team api key lookup connectivity error provider={} baseUrl={} cause={}",
                        normalizedProvider,
                        baseUrl,
                        rootCauseClassName(ex)
                );
            }
        }
        throw new TeamApiKeyLookupUnavailableException(
                "team-service에 연결할 수 없어 팀 API 키 중복 검증에 실패했습니다");
    }

    private static boolean isRecoverableConnectivityError(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof UnresolvedAddressException || cursor instanceof ConnectException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String rootCauseClassName(Throwable ex) {
        Throwable cursor = ex;
        Throwable last = ex;
        while (cursor != null) {
            last = cursor;
            cursor = cursor.getCause();
        }
        return last.getClass().getSimpleName();
    }

    private static List<String> resolveCandidateBaseUrls(String configuredBaseUrl) {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, configuredBaseUrl);
        if (configuredBaseUrl != null && configuredBaseUrl.contains("team-service")) {
            addCandidate(candidates, LOCALHOST_TEAM_SERVICE_BASE_URL);
            addCandidate(candidates, HOST_DOCKER_INTERNAL_TEAM_SERVICE_BASE_URL);
        }
        if (candidates.isEmpty()) {
            addCandidate(candidates, DOCKER_TEAM_SERVICE_BASE_URL);
            addCandidate(candidates, LOCALHOST_TEAM_SERVICE_BASE_URL);
            addCandidate(candidates, HOST_DOCKER_INTERNAL_TEAM_SERVICE_BASE_URL);
        }
        return new ArrayList<>(candidates);
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
