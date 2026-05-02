package com.eevee.usageservice.service;

import com.eevee.usage.events.UsagePredictionSignalsEvent;
import com.eevee.usageservice.config.UsageRabbitProperties;
import com.eevee.usageservice.api.dto.UsageTeamUserSlice;
import com.eevee.usageservice.repository.analytics.UsageAnalyticsJdbcRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Publishes {@link UsagePredictionSignalsEvent} to the usage topic exchange for agent-service and other consumers.
 */
@Component
@ConditionalOnProperty(prefix = "usage.rabbit.outbound-prediction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsagePredictionSignalsPublisher {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int SLICE_LOOKBACK_DAYS = 14;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final UsageRabbitProperties rabbitProperties;
    private final UsageAnalyticsJdbcRepository analyticsRepository;
    private final UsagePredictionSignalsBuilder builder;

    @Value("${usage.prediction-signals.schedule.enabled:true}")
    private boolean predictionScheduleEnabled;

    public UsagePredictionSignalsPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            UsageRabbitProperties rabbitProperties,
            UsageAnalyticsJdbcRepository analyticsRepository,
            UsagePredictionSignalsBuilder builder
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.rabbitProperties = rabbitProperties;
        this.analyticsRepository = analyticsRepository;
        this.builder = builder;
    }

    @Scheduled(cron = "${usage.prediction-signals.cron:0 15 0 * * *}", zone = "Asia/Seoul")
    public void scheduledDailyPublish() {
        if (!predictionScheduleEnabled) {
            return;
        }
        publishForAsOfDate(LocalDate.now(KST));
    }

    /**
     * Publishes one event per active (team, user) slice for the given KST "as of" date.
     */
    public void publishForAsOfDate(LocalDate asOfKst) {
        LocalDate minUsageDate = asOfKst.minusDays(SLICE_LOOKBACK_DAYS - 1);
        for (UsageTeamUserSlice slice : analyticsRepository.findDistinctTeamUserSlices(minUsageDate)) {
            builder.build(slice, asOfKst).ifPresent(this::send);
        }
    }

    private void send(UsagePredictionSignalsEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setHeader("teamId", event.teamId());
            props.setHeader("userId", event.userId());
            props.setHeader("schemaVersion", event.schemaVersion());
            Message amqpMessage = new Message(payload, props);
            rabbitTemplate.send(
                    rabbitProperties.getOutboundPrediction().getExchange(),
                    rabbitProperties.getOutboundPrediction().getRoutingKey(),
                    amqpMessage
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize UsagePredictionSignalsEvent", e);
        }
    }
}
