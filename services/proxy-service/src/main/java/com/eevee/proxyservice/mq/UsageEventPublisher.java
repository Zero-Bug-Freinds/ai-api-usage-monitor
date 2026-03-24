package com.eevee.proxyservice.mq;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class UsageEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ProxyProperties proxyProperties;

    public UsageEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            ProxyProperties proxyProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.proxyProperties = proxyProperties;
    }

    public Mono<Void> publish(UsageRecordedEvent event) {
        return Mono.fromRunnable(() -> {
                    try {
                        String json = objectMapper.writeValueAsString(event);
                        rabbitTemplate.convertAndSend(
                                proxyProperties.getRabbit().getUsageExchange(),
                                proxyProperties.getRabbit().getUsageRoutingKey(),
                                json
                        );
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("usage event serialization failed", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
