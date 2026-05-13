package com.zerobugfreinds.ai_agent_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiAgentDeepseekHttpClientConfiguration {

	public static final String DEEPSEEK_REST_CLIENT = "deepseekRestClient";

	@Bean(name = DEEPSEEK_REST_CLIENT)
	public RestClient deepseekRestClient(AiAgentDeepseekProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(properties.resolvedConnectTimeoutMs()))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(properties.resolvedReadTimeoutMs()));
		return RestClient.builder().requestFactory(requestFactory).build();
	}
}
