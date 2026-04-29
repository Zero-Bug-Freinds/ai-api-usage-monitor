package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.InternalTeamMembershipVerifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;

/**
 * team-service 내부 API를 호출해 팀 멤버십을 검증한다.
 */
@Component
public class TeamMembershipVerificationClient {
    private static final Logger log = LoggerFactory.getLogger(TeamMembershipVerificationClient.class);

    private static final String DOCKER_TEAM_SERVICE_BASE_URL = "http://team-service:8093";
    private static final String LOCALHOST_TEAM_SERVICE_BASE_URL = "http://localhost:8093";
    private static final String HOST_DOCKER_INTERNAL_TEAM_SERVICE_BASE_URL = "http://host.docker.internal:8093";

    private final List<RestClient> restClients;
    private final List<String> baseUrls;

    public TeamMembershipVerificationClient(
            RestClient.Builder restClientBuilder,
            @Value("${identity.team-service.internal-base-url:${TEAM_SERVICE_INTERNAL_URL:http://team-service:8093}}")
            String teamServiceBaseUrl
    ) {
        this.baseUrls = resolveCandidateBaseUrls(teamServiceBaseUrl);
        this.restClients = this.baseUrls.stream()
                .map(baseUrl -> restClientBuilder.baseUrl(baseUrl).build())
                .toList();
        log.info("team membership verification baseUrls={}", this.baseUrls);
    }

    public boolean isActiveTeamMember(Long teamId, Long userId) {
        if (userId == null) {
            return false;
        }
        return isActiveTeamMember(teamId, String.valueOf(userId));
    }

    public boolean isActiveTeamMember(Long teamId, String userIdOrEmail) {
        if (teamId == null || userIdOrEmail == null || userIdOrEmail.isBlank()) {
            return false;
        }
        String normalized = userIdOrEmail.trim();
        for (int idx = 0; idx < restClients.size(); idx++) {
            RestClient client = restClients.get(idx);
            String baseUrl = baseUrls.get(idx);
            try {
                ApiResponse<InternalTeamMembershipVerifyResponse> response = client.get()
                        .uri("/internal/v1/teams/{teamId}/members/{userId}/verify", teamId, normalized)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                boolean matched = response != null
                        && response.success()
                        && response.data() != null
                        && response.data().isValid();
                if (!matched) {
                    log.info(
                            "team membership verification failed teamId={} identifier={} baseUrl={}",
                            teamId,
                            normalized,
                            baseUrl
                    );
                }
                return matched;
            } catch (HttpClientErrorException.NotFound ex) {
                log.info(
                        "team membership verification not found teamId={} identifier={} baseUrl={}",
                        teamId,
                        normalized,
                        baseUrl
                );
                return false;
            } catch (HttpClientErrorException ex) {
                log.warn(
                        "team membership verification http error teamId={} identifier={} status={} baseUrl={}",
                        teamId,
                        normalized,
                        ex.getStatusCode(),
                        baseUrl
                );
                return false;
            } catch (RuntimeException ex) {
                if (!isRecoverableConnectivityError(ex)) {
                    throw ex;
                }
                log.warn(
                        "team membership verification connectivity error teamId={} identifier={} baseUrl={} cause={}",
                        teamId,
                        normalized,
                        baseUrl,
                        rootCauseClassName(ex)
                );
            }
        }
        log.warn("team membership verification unreachable teamId={} identifier={}", teamId, normalized);
        return false;
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
