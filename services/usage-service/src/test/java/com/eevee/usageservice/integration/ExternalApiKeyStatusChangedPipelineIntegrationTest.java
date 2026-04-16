package com.eevee.usageservice.integration;

import com.eevee.usageservice.mq.ExternalApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExternalApiKeyStatusChangedPipelineIntegrationTest {

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
        r.add("usage.gateway.shared-secret", () -> "test-secret");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiKeyMetadataRepository repository;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Value("${usage.rabbit.identity-api-key.queue}")
    private String identityApiKeyQueueName;

    @BeforeEach
    void waitForQueueDeclarations() {
        await().atMost(20, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> amqpAdmin.getQueueProperties(identityApiKeyQueueName) != null);
    }

    @Test
    void registerUpdateDeleteEvents_areConsumedAndUpsertedWithoutPhysicalDelete() throws Exception {
        ExternalApiKeyStatusChangedEvent registered = new ExternalApiKeyStatusChangedEvent(
                1,
                Instant.parse("2026-04-15T10:00:00Z"),
                101L,
                "GoogleTestKey1",
                7L,
                "GOOGLE",
                ExternalApiKeyStatus.ACTIVE
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(registered)
        );

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> repository.findById("101").isPresent());

        var active = repository.findById("101").orElseThrow();
        assertThat(active.getAlias()).isEqualTo("GoogleTestKey1");
        assertThat(active.getStatus().name()).isEqualTo("ACTIVE");

        ExternalApiKeyStatusChangedEvent aliasUpdated = new ExternalApiKeyStatusChangedEvent(
                1,
                Instant.parse("2026-04-15T10:01:00Z"),
                101L,
                "GoogleTestKey1-Renamed",
                7L,
                "GOOGLE",
                ExternalApiKeyStatus.ACTIVE
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(aliasUpdated)
        );

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var updated = repository.findById("101").orElseThrow();
                    assertThat(updated.getAlias()).isEqualTo("GoogleTestKey1-Renamed");
                    assertThat(updated.getStatus().name()).isEqualTo("ACTIVE");
                });

        ExternalApiKeyStatusChangedEvent deleted = new ExternalApiKeyStatusChangedEvent(
                1,
                Instant.parse("2026-04-15T10:02:00Z"),
                101L,
                "GoogleTestKey1-Renamed",
                7L,
                "GOOGLE",
                ExternalApiKeyStatus.DELETED
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(deleted)
        );

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var deletedRow = repository.findById("101").orElseThrow();
                    assertThat(deletedRow.getAlias()).isEqualTo("GoogleTestKey1-Renamed");
                    assertThat(deletedRow.getStatus().name()).isEqualTo("DELETED");
                });
    }
}
