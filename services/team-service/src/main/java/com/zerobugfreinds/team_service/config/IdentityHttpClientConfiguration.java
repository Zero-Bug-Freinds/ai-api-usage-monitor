package com.zerobugfreinds.team_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class IdentityHttpClientConfiguration {

    @Bean
    public HttpClient identityServiceHttpClient(
            @Value("${identity.http.connect-timeout-ms:3000}") int connectTimeoutMs
    ) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, connectTimeoutMs)))
                .build();
    }
}
