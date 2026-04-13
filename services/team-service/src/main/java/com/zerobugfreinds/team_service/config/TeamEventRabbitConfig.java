package com.zerobugfreinds.team_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 팀 이벤트 교환기 선언.
 */
@Configuration
public class TeamEventRabbitConfig {

	@Bean
	public TopicExchange teamMemberAddedExchange(
			@Value("${team.member-added-event.exchange:team.events}") String exchangeName
	) {
		return new TopicExchange(exchangeName, true, false);
	}
}
