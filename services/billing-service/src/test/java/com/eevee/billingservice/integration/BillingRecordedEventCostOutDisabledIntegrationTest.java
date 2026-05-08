package com.eevee.billingservice.integration;

import com.eevee.billingservice.repository.BillingProcessedEventRepository;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * When cost-out is disabled, billable rows are stored as not cost-event-applicable and no outbound message is sent.
 */
@SpringBootTest
@TestPropertySource(properties = "billing.rabbit.cost-out.enabled=false")
class BillingRecordedEventCostOutDisabledIntegrationTest extends AbstractBillingIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BillingProcessedEventRepository processedEventRepository;

    @Test
    void billablePath_marksNotApplicable_andDoesNotPublishCostEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-06-01T12:00:00Z"),
                "corr-disabled",
                "user-disabled",
                null,
                null,
                "key-disabled",
                null,
                "cafebabedeadbeef",
                "managed",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 1L, 2L, 3L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                true,
                200
        );

        String json = objectMapper.writeValueAsString(event);
        rabbitTemplate.convertAndSend("usage.events", "usage.recorded", json);

        await().atMost(15, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> processedEventRepository.existsById(eventId));

        var row = processedEventRepository.findById(eventId).orElseThrow();
        assertThat(row.isCostEventApplicable()).isFalse();
        assertThat(row.getCostEventPublishedAt()).isNull();
    }
}
