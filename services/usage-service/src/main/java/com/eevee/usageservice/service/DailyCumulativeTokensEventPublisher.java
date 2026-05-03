package com.eevee.usageservice.service;

import com.eevee.usage.events.DailyCumulativeTokensUpdatedEvent;
import com.eevee.usageservice.config.UsageRabbitProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link DailyCumulativeTokensUpdatedEvent} after {@code daily_cumulative_token_by_scope} is updated.
 */
@Component
@ConditionalOnProperty(
        prefix = "usage.rabbit.outbound-daily-cumulative-tokens",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DailyCumulativeTokensEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final UsageRabbitProperties rabbitProperties;

    public DailyCumulativeTokensEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            UsageRabbitProperties rabbitProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.rabbitProperties = rabbitProperties;
    }

    public void publish(DailyCumulativeTokensUpdatedEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setHeader("userId", event.userId());
            props.setHeader("teamId", event.teamId());
            props.setHeader("schemaVersion", event.schemaVersion());
            props.setHeader("sourceEventId", event.sourceEventId().toString());
            Message amqpMessage = new Message(payload, props);
            rabbitTemplate.send(
                    rabbitProperties.getOutboundDailyCumulativeTokens().getExchange(),
                    rabbitProperties.getOutboundDailyCumulativeTokens().getRoutingKey(),
                    amqpMessage
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DailyCumulativeTokensUpdatedEvent", e);
        }
    }
}
