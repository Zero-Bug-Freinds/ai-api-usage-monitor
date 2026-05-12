package com.zerobugfreinds.team_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class BillingRestClientConfiguration {

    @Bean
    RestClient billingServiceRestClient(
            @Value("${team.billing.base-url:http://localhost:8095}") String billingBaseUrl,
            @Value("${team.billing.http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${team.billing.http.read-timeout-ms:15000}") int readTimeoutMs
    ) {
        String base = StringUtils.hasText(billingBaseUrl) ? billingBaseUrl.trim() : "http://localhost:8095";
        base = base.replaceAll("/+$", "");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(1, connectTimeoutMs)));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1, readTimeoutMs)));
        return RestClient.builder()
                .baseUrl(base)
                .requestFactory(requestFactory)
                .build();
    }
}
