package com.eevee.usageservice.consumer;

import com.eevee.usageservice.mq.UsageSummaryAggregationMessage;
import com.eevee.usageservice.service.UsageAggregationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "usage.rabbit.summary-aggregation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsageSummaryAggregationEventListener {

    private static final Logger log = LoggerFactory.getLogger(UsageSummaryAggregationEventListener.class);

    private final ObjectMapper objectMapper;
    private final UsageAggregationService usageAggregationService;

    public UsageSummaryAggregationEventListener(ObjectMapper objectMapper, UsageAggregationService usageAggregationService) {
        this.objectMapper = objectMapper;
        this.usageAggregationService = usageAggregationService;
    }

    @RabbitListener(
            queues = "${usage.rabbit.summary-aggregation.queue}",
            containerFactory = "summaryAggregationRabbitListenerContainerFactory"
    )
    public void onMessage(String json) {
        try {
            UsageSummaryAggregationMessage message = parseMessage(json);
            usageAggregationService.applyFromEvent(message);
        } catch (DataAccessException e) {
            log.error("Transient DB error during summary aggregation, will retry", e);
            throw new IllegalStateException("summary aggregation db error", e);
        } catch (Exception e) {
            log.error("Failed to process summary aggregation message", e);
            throw new IllegalStateException("summary aggregation handling failed", e);
        }
    }

    private UsageSummaryAggregationMessage parseMessage(String json) throws JsonProcessingException {
        try {
            return objectMapper.readValue(json, UsageSummaryAggregationMessage.class);
        } catch (JsonProcessingException ex) {
            // Backward-compat: recover malformed legacy payloads like {eventId:...,userId:...}
            UsageSummaryAggregationMessage legacy = tryParseLegacyPayload(json);
            if (legacy != null) {
                log.warn("Parsed malformed summary payload with legacy fallback parser");
                return legacy;
            }
            throw ex;
        }
    }

    private UsageSummaryAggregationMessage tryParseLegacyPayload(String payload) {
        if (payload == null) {
            return null;
        }
        String trimmed = payload.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        if (trimmed.contains("\"")) {
            return null;
        }
        Map<String, String> values = parseLegacyKeyValuePayload(trimmed);
        try {
            String eventIdRaw = values.get("eventId");
            String occurredAtRaw = values.get("occurredAt");
            String userIdRaw = values.get("userId");
            String providerRaw = values.get("provider");
            String modelRaw = values.get("model");
            if (eventIdRaw == null || occurredAtRaw == null || userIdRaw == null || providerRaw == null || modelRaw == null) {
                return null;
            }
            return new UsageSummaryAggregationMessage(
                    UUID.fromString(eventIdRaw),
                    toInstantFromEpochDecimal(occurredAtRaw),
                    nullIfLiteralNull(values.get("teamId")),
                    userIdRaw,
                    providerRaw,
                    modelRaw,
                    parseLong(values.get("requestCount")),
                    parseLong(values.get("successCount")),
                    parseLong(values.get("errorCount")),
                    parseLong(values.get("totalTokens")),
                    parseLong(values.get("promptTokens")),
                    parseLong(values.get("completionTokens")),
                    parseLong(values.get("reasoningTokens")),
                    parseBigDecimal(values.get("totalCost"))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, String> parseLegacyKeyValuePayload(String payload) {
        String body = payload.substring(1, payload.length() - 1);
        String[] pairs = body.split(",");
        Map<String, String> out = new HashMap<>();
        for (String pair : pairs) {
            int idx = pair.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = pair.substring(0, idx).trim();
            String value = pair.substring(idx + 1).trim();
            out.put(key, value);
        }
        return out;
    }

    private static Instant toInstantFromEpochDecimal(String raw) {
        BigDecimal epoch = new BigDecimal(raw);
        long seconds = epoch.longValue();
        int nanos = epoch.subtract(BigDecimal.valueOf(seconds))
                .movePointRight(9)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static String nullIfLiteralNull(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private static long parseLong(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return 0L;
        }
        return Long.parseLong(raw);
    }

    private static BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(raw);
    }
}
