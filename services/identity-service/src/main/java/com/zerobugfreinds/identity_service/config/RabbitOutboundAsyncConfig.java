package com.zerobugfreinds.identity_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 트랜잭션 커밋 이후 RabbitMQ 발행 리스너를 요청 스레드에서 분리한다.
 */
@Configuration
@EnableAsync
public class RabbitOutboundAsyncConfig {

	/**
	 * {@code @TransactionalEventListener} + {@code @Async} 에서 참조하는 Executor 이름.
	 */
	public static final String RABBIT_TRANSACTIONAL_OUTBOUND_EXECUTOR = "rabbitTransactionalOutboundExecutor";

	@Bean(name = RABBIT_TRANSACTIONAL_OUTBOUND_EXECUTOR)
	public Executor rabbitTransactionalOutboundExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(16);
		executor.setQueueCapacity(2000);
		executor.setThreadNamePrefix("rabbit-tx-out-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
		return executor;
	}
}
