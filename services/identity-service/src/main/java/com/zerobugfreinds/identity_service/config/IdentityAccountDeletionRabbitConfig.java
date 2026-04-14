package com.zerobugfreinds.identity_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 회원 탈퇴 요청·ACK 용 교환기·큐. 다른 서비스는 동일 exchange 에 요청/ACK 라우팅 키로 바인딩한다.
 */
@Configuration
public class IdentityAccountDeletionRabbitConfig {

	@Bean
	public TopicExchange identityAccountDeletionExchange(
			@Value("${identity.account-deletion-event.exchange:identity.events}") String exchangeName
	) {
		return new TopicExchange(exchangeName, true, false);
	}

	@Bean
	public Queue identityAccountDeletionAckQueue(
			@Value("${identity.account-deletion-ack.queue:identity.account-deletion.ack.queue}") String queueName
	) {
		return new Queue(queueName, true);
	}

	@Bean
	public Binding identityAccountDeletionAckBinding(
			Queue identityAccountDeletionAckQueue,
			TopicExchange identityAccountDeletionExchange,
			@Value("${identity.account-deletion-ack.routing-key:identity.user.account-deletion-ack}") String routingKey
	) {
		return BindingBuilder.bind(identityAccountDeletionAckQueue)
				.to(identityAccountDeletionExchange)
				.with(routingKey);
	}
}
