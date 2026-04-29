package com.eevee.llmproxyservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient geminiWebClient(
            WebClient.Builder webClientBuilder,
            @Value("${google.gemini.base-url}") String geminiBaseUrl
    ) {
        return webClientBuilder
                .baseUrl(geminiBaseUrl)
                .build();
    }
}
