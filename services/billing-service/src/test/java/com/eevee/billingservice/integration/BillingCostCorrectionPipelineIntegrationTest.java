package com.eevee.billingservice.integration;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.events.BillingCostCorrectedEvent;
import com.eevee.billingservice.events.BillingCostCorrectionAmqp;
import com.eevee.billingservice.repository.BillingCostCorrectionProcessedRepository;
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
import java.time.LocalDate;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("resource")
@SpringBootTest
@Import(BillingCostCorrectionPipelineIntegrationTest.CorrectedOutAmqpTestConfig.class)
@Testcontainers
class BillingCostCorrectionPipelineIntegrationTest {

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
    private BillingCostCorrectionProcessedRepository correctionProcessedRepository;

    static final String IT_CORRECTED_QUEUE = "it.billing.cost.corrected";

    @TestConfiguration
    static class CorrectedOutAmqpTestConfig {

        @Bean
        Queue integrationCorrectedTestQueue() {
            return new Queue(IT_CORRECTED_QUEUE, true);
        }

        @Bean
        Binding integrationCorrectedTestBinding(
                Queue integrationCorrectedTestQueue,
                @Qualifier("billingCostEventsExchange") TopicExchange billingCostEventsExchange,
                BillingRabbitProperties props
        ) {
            return BindingBuilder.bind(integrationCorrectedTestQueue)
                    .to(billingCostEventsExchange)
                    .with(props.getCorrectionOut().getRoutingKey());
        }
    }

    @Test
    void correctionPublishedOnce_andReplayIsIdempotent() throws Exception {
        UUID correctionId = UUID.randomUUID();
        BillingCostCorrectionAmqp cmd = new BillingCostCorrectionAmqp(
                BillingCostCorrectionAmqp.CURRENT_SCHEMA_VERSION,
                correctionId,
                "user-corr-it",
                "key-corr-it",
                LocalDate.of(2025, 6, 1),
                new BigDecimal("0.42"),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        String json = objectMapper.writeValueAsString(cmd);
        rabbitTemplate.convertAndSend("billing.events", "billing.cost.correct", json);

        await().atMost(15, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> correctionProcessedRepository.existsById(correctionId));

        var out1 = rabbitTemplate.receive(IT_CORRECTED_QUEUE, 8000);
        assertThat(out1).isNotNull();
        BillingCostCorrectedEvent evt = objectMapper.readValue(out1.getBody(), BillingCostCorrectedEvent.class);
        assertThat(evt.correctionEventId()).isEqualTo(correctionId);
        assertThat(evt.schemaVersion()).isEqualTo(BillingCostCorrectedEvent.CURRENT_SCHEMA_VERSION);
        assertThat(evt.appliedDeltaCostUsd()).isEqualByComparingTo("0.42");

        rabbitTemplate.convertAndSend("billing.events", "billing.cost.correct", json);

        await().pollDelay(800, java.util.concurrent.TimeUnit.MILLISECONDS).until(() -> true);

        var out2 = rabbitTemplate.receive(IT_CORRECTED_QUEUE, 2000);
        assertThat(out2).isNull();
    }
}
