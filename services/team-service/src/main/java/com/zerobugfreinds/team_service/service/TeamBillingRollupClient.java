package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.dto.BillingTeamMonthRollupRequest;
import com.zerobugfreinds.team_service.dto.BillingTeamMonthRollupResponse;
import com.zerobugfreinds.team_service.security.GatewayHeaderContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeamBillingRollupClient {

    private static final String HDR_GATEWAY_AUTH = "X-Gateway-Auth";

    private final RestClient billingServiceRestClient;

    public TeamBillingRollupClient(RestClient billingServiceRestClient) {
        this.billingServiceRestClient = billingServiceRestClient;
    }

    public BillingTeamMonthRollupResponse postTeamMonthRollup(
            HttpServletRequest incoming,
            BillingTeamMonthRollupRequest body
    ) {
        String userId = incoming.getHeader(GatewayHeaderContextFilter.USER_ID_HEADER);
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id가 필요합니다");
        }
        String gatewayAuth = incoming.getHeader(HDR_GATEWAY_AUTH);
        try {
            return billingServiceRestClient.post()
                    .uri("/api/v1/expenditure/team/month-rollup")
                    .header(GatewayHeaderContextFilter.USER_ID_HEADER, userId.trim())
                    .headers(h -> {
                        if (StringUtils.hasText(gatewayAuth)) {
                            h.set(HDR_GATEWAY_AUTH, gatewayAuth.trim());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(BillingTeamMonthRollupResponse.class);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.resolve(e.getStatusCode().value()), e.getMessage(), e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "billing-service 연결 실패", e);
        }
    }
}
