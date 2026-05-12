package com.zerobugfreinds.ai_agent_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AiAgentGeminiHttpClientConfiguration {

	public static final String GEMINI_REST_CLIENT = "geminiRestClient";
	public static final String GEMINI_BATCH_EXECUTOR = "geminiBatchExecutor";

	@Bean(name = GEMINI_REST_CLIENT)
	public RestClient geminiRestClient(AiAgentGeminiProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(properties.resolvedConnectTimeoutMs()))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(properties.resolvedReadTimeoutMs()));
		return RestClient.builder().requestFactory(requestFactory).build();
	}

	@Bean(name = GEMINI_BATCH_EXECUTOR, destroyMethod = "shutdown")
	public ExecutorService geminiBatchExecutor(AiAgentGeminiProperties properties) {
		int poolSize = properties.resolvedBatchParallelism();
		ThreadFactory threadFactory = new ThreadFactory() {
			private final AtomicInteger seq = new AtomicInteger();

			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "gemini-batch-" + seq.incrementAndGet());
				t.setDaemon(true);
				return t;
			}
		};
		return Executors.newFixedThreadPool(poolSize, threadFactory);
	}
}
