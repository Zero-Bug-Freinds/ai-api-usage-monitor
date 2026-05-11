package com.zerobugfreinds.team_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class BillingRestClientConfiguration {

    @Bean
    RestClient billingServiceRestClient(
            @Value("${team.billing.base-url:http://localhost:8095}") String billingBaseUrl
    ) {
        String base = StringUtils.hasText(billingBaseUrl) ? billingBaseUrl.trim() : "http://localhost:8095";
        base = base.replaceAll("/+$", "");
        return RestClient.builder().baseUrl(base).build();
    }
}
