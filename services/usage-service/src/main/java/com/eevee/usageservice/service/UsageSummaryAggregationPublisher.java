package com.eevee.usageservice.service;

import com.eevee.usageservice.config.UsageRabbitProperties;
import com.eevee.usageservice.mq.UsageSummaryAggregationMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(prefix = "usage.rabbit.summary-aggregation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsageSummaryAggregationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final UsageRabbitProperties rabbitProperties;

    public UsageSummaryAggregationPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            UsageRabbitProperties rabbitProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.rabbitProperties = rabbitProperties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAfterCommit(UsageSummaryAggregationRequestedEvent event) {
        UsageSummaryAggregationMessage message = new UsageSummaryAggregationMessage(
                event.eventId(),
                event.occurredAt(),
                event.teamId(),
                event.userId(),
                event.provider().name(),
                event.model(),
                1L,
                event.requestSuccessful() ? 1L : 0L,
                isError(event.requestSuccessful(), event.upstreamStatusCode()) ? 1L : 0L,
                defaultLong(event.totalTokens()),
                defaultLong(event.promptTokens()),
                defaultLong(event.completionTokens()),
                defaultLong(event.reasoningTokens()),
                defaultCost(event.estimatedCost())
        );
        try {
            byte[] body = objectMapper.writeValueAsBytes(message);
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding("UTF-8");
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            Message amqpMessage = new Message(body, messageProperties);
            rabbitTemplate.convertAndSend(
                    rabbitProperties.getSummaryAggregation().getExchange(),
                    rabbitProperties.getSummaryAggregation().getRoutingKey(),
                    amqpMessage
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize UsageSummaryAggregationMessage", e);
        }
    }

    private static long defaultLong(Long value) {
        return value != null ? value : 0L;
    }

    private static BigDecimal defaultCost(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static boolean isError(boolean requestSuccessful, Integer upstreamStatusCode) {
        return !requestSuccessful || (upstreamStatusCode != null && upstreamStatusCode >= 400);
    }
}
