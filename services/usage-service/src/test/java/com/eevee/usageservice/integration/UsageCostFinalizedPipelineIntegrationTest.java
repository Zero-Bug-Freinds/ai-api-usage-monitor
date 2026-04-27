package com.eevee.usageservice.integration;

import com.eevee.usage.events.UsageCostEventAmqp;
import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UsageCostFinalizedPipelineIntegrationTest {

    @Container
    static RabbitMQContainer rabbit = UsageIntegrationContainers.rabbitMq();

    @Container
    static PostgreSQLContainer<?> postgres = UsageIntegrationContainers.postgres();

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.rabbitmq.host", rabbit::getHost);
        r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        r.add("spring.rabbitmq.username", () -> "guest");
        r.add("spring.rabbitmq.password", () -> "guest");
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // ✅ integration tests rely on JPA create-drop; disable Flyway migrations
        r.add("spring.flyway.enabled", () -> "false");
        r.add("usage.gateway.shared-secret", () -> "test-secret");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsageRecordedLogRepository repository;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Value("${usage.rabbit.queue}")
    private String usageQueueName;

    @Value("${usage.rabbit.cost-finalized.queue}")
    private String usageCostFinalizedQueueName;

    @BeforeEach
    void waitForQueueDeclarations() {
        await().atMost(20, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() ->
                        amqpAdmin.getQueueProperties(usageQueueName) != null
                                && amqpAdmin.getQueueProperties(usageCostFinalizedQueueName) != null);
    }

    @Test
    void usageRowThenCostFinalizedEvent_updatesEstimatedCost() throws Exception {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent recorded = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-06-01T12:00:00Z"),
                "corr-cost",
                "user-cost",
                null,
                null,
                "key-cost",
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

        rabbitTemplate.convertAndSend("usage.events", "usage.recorded", objectMapper.writeValueAsString(recorded));

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> repository.existsByEventId(eventId));

        assertThat(repository.findById(eventId)).isPresent();
        assertThat(repository.findById(eventId).orElseThrow().getEstimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);

        UsageCostFinalizedEvent finalized = UsageCostFinalizedEvent.v1(eventId, new BigDecimal("0.042"));
        rabbitTemplate.convertAndSend(
                UsageCostEventAmqp.TOPIC_EXCHANGE_NAME,
                UsageCostEventAmqp.ROUTING_KEY_COST_FINALIZED,
                objectMapper.writeValueAsString(finalized));

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(repository.findById(eventId).orElseThrow().getEstimatedCost())
                        .isEqualByComparingTo("0.042"));
    }

    /**
     * Cost-finalized can be delivered before {@code usage.recorded} finishes inserting the row
     * (parallel consumers). Retries must still apply the cost after the row appears.
     */
    @Test
    void costFinalizedBeforeUsageRow_stillUpdatesEstimatedCost() throws Exception {
        UUID eventId = UUID.randomUUID();
        UsageCostFinalizedEvent finalized = UsageCostFinalizedEvent.v1(eventId, new BigDecimal("0.099"));
        rabbitTemplate.convertAndSend(
                UsageCostEventAmqp.TOPIC_EXCHANGE_NAME,
                UsageCostEventAmqp.ROUTING_KEY_COST_FINALIZED,
                objectMapper.writeValueAsString(finalized));

        Thread.sleep(200);

        UsageRecordedEvent recorded = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-06-01T12:00:00Z"),
                "corr-late",
                "user-cost",
                null,
                null,
                "key-cost",
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
        rabbitTemplate.convertAndSend("usage.events", "usage.recorded", objectMapper.writeValueAsString(recorded));

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(repository.findById(eventId).orElseThrow().getEstimatedCost())
                        .isEqualByComparingTo("0.099"));
    }
}
