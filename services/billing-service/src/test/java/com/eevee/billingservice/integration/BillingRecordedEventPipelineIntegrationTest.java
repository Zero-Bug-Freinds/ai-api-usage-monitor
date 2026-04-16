package com.eevee.billingservice.integration;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.repository.BillingProcessedEventRepository;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usage.events.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

/**
 * Testcontainers implement {@link AutoCloseable}; JUnit 5 + {@link org.testcontainers.junit.jupiter.Container}
 * close them after the suite. {@code @SuppressWarnings("resource")} avoids false-positive “unassigned Closeable”
 * on fluent configuration chains.
 */
@SuppressWarnings("resource")
@SpringBootTest
@Import(BillingRecordedEventPipelineIntegrationTest.CostFinalizedAmqpTestConfig.class)
@Testcontainers
class BillingRecordedEventPipelineIntegrationTest {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app");

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
        r.add("billing.gateway.shared-secret", () -> "test-secret");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BillingProcessedEventRepository processedEventRepository;

    static final String IT_COST_QUEUE = "it.usage.cost.finalized";

    @TestConfiguration
    static class CostFinalizedAmqpTestConfig {

        @Bean
        Queue integrationCostTestQueue() {
            return new Queue(IT_COST_QUEUE, true);
        }

        @Bean
        Binding integrationCostTestBinding(
                Queue integrationCostTestQueue,
                @Qualifier("billingCostEventsExchange") TopicExchange billingCostEventsExchange,
                BillingRabbitProperties props
        ) {
            return BindingBuilder.bind(integrationCostTestQueue)
                    .to(billingCostEventsExchange)
                    .with(props.getCostOut().getRoutingKey());
        }
    }

    @Test
    void jsonPublishedLikeProxy_isConsumedAndMarkedProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-06-01T12:00:00Z"),
                "corr-it",
                "user-it",
                null,
                null,
                "key-it",
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

        assertThat(processedEventRepository.findById(eventId)).isPresent();

        await().atMost(15, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> processedEventRepository.findById(eventId)
                        .map(r -> r.getCostEventPublishedAt() != null)
                        .orElse(false));

        var costMsg = rabbitTemplate.receive(IT_COST_QUEUE, 5000);
        assertThat(costMsg).isNotNull();
        UsageCostFinalizedEvent costEvent = objectMapper.readValue(costMsg.getBody(), UsageCostFinalizedEvent.class);
        assertThat(costEvent.eventId()).isEqualTo(eventId);
        assertThat(costEvent.estimatedCostUsd()).isNotNull();
        assertThat(costEvent.schemaVersion()).isEqualTo(UsageCostFinalizedEvent.CURRENT_SCHEMA_VERSION);
    }
}
