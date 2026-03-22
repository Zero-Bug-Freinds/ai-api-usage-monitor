package com.eevee.proxygateway.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange usageExchange(ProxyProperties proxyProperties) {
        return new TopicExchange(proxyProperties.getRabbit().getUsageExchange(), true, false);
    }
}
