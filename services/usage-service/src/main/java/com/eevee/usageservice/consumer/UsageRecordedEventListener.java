package com.eevee.usageservice.consumer;

import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.service.UsageRecordedService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Component;

/**
 * Consumes JSON payloads published by {@code proxy-service} (same format as {@link UsageRecordedEvent}).
 */
@Component
public class UsageRecordedEventListener {

    private static final Logger log = LoggerFactory.getLogger(UsageRecordedEventListener.class);

    private final ObjectMapper objectMapper;
    private final UsageRecordedService usageRecordedService;

    public UsageRecordedEventListener(ObjectMapper objectMapper, UsageRecordedService usageRecordedService) {
        this.objectMapper = objectMapper;
        this.usageRecordedService = usageRecordedService;
    }

    @RabbitListener(queues = "${usage.rabbit.queue}")
    public void onMessage(String json) {
        try {
            UsageRecordedEvent event = objectMapper.readValue(json, UsageRecordedEvent.class);
            usageRecordedService.persist(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize UsageRecordedEvent (malformed JSON); message will be acked to avoid requeue loop", e);
        } catch (InvalidDataAccessResourceUsageException e) {
            log.error(
                    "Non-retryable persistence error for UsageRecordedEvent (e.g. SQL type mismatch); "
                            + "message will be acked to avoid requeue storm. Fix schema or mapping and replay if needed.",
                    e
            );
        } catch (Exception e) {
            log.error("Failed to persist UsageRecordedEvent; rethrowing for possible transient retry", e);
            throw new IllegalStateException("usage event handling failed", e);
        }
    }
}
