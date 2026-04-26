package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.InternalTeamMembershipVerifyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * team-service 내부 API를 호출해 팀 멤버십을 검증한다.
 */
@Component
public class TeamMembershipVerificationClient {

    private final RestClient restClient;

    public TeamMembershipVerificationClient(
            RestClient.Builder restClientBuilder,
            @Value("${identity.team-service.internal-base-url:${TEAM_SERVICE_INTERNAL_URL:http://team-service:8093}}")
            String teamServiceBaseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(teamServiceBaseUrl)
                .build();
    }

    public boolean isActiveTeamMember(Long teamId, Long userId) {
        try {
            ApiResponse<InternalTeamMembershipVerifyResponse> response = restClient.get()
                    .uri("/internal/v1/teams/{teamId}/members/{userId}/verify", teamId, String.valueOf(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null
                    && response.success()
                    && response.data() != null
                    && response.data().isValid();
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        }
    }
}
