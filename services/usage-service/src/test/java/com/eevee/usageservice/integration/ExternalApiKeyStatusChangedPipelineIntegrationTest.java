package com.eevee.usageservice.integration;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.mq.ExternalApiKeyDeletedEvent;
import com.eevee.usageservice.mq.ExternalApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.IdentityExternalApiKeyEventTypes;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
    private UsageRecordedLogRepository usageRecordedLogRepository;

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

        ExternalApiKeyDeletedEvent deleted = new ExternalApiKeyDeletedEvent(
                IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED,
                7L,
                101L,
                Instant.parse("2026-04-15T10:02:00Z"),
                true,
                "GOOGLE",
                "GoogleTestKey1-Renamed"
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

    @Test
    void deletedEvent_retainLogsFalse_removesUsageLogsAndMetadata() throws Exception {
        ExternalApiKeyStatusChangedEvent registered = new ExternalApiKeyStatusChangedEvent(
                1,
                Instant.parse("2026-04-16T10:00:00Z"),
                303L,
                "KeyToPurge",
                9L,
                "GOOGLE",
                ExternalApiKeyStatus.ACTIVE
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(registered)
        );
        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> repository.findById("303").isPresent());

        UsageRecordedLogEntity log = new UsageRecordedLogEntity(
                UUID.randomUUID(),
                Instant.parse("2026-04-16T09:00:00Z"),
                null,
                "9",
                null,
                null,
                "303",
                "fp",
                "managed",
                AiProvider.GOOGLE,
                "gemini",
                1L,
                1L,
                2L,
                0L,
                null,
                BigDecimal.ZERO,
                "/p",
                "h.example",
                false,
                true,
                200,
                Instant.now()
        );
        usageRecordedLogRepository.save(log);
        assertThat(usageRecordedLogRepository.countByApiKeyId("303")).isEqualTo(1L);

        ExternalApiKeyDeletedEvent purge = new ExternalApiKeyDeletedEvent(
                IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED,
                9L,
                303L,
                Instant.parse("2026-04-16T12:00:00Z"),
                false,
                "GOOGLE",
                "KeyToPurge"
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(purge)
        );

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(repository.findById("303")).isEmpty();
                    assertThat(usageRecordedLogRepository.countByApiKeyId("303")).isEqualTo(0L);
                });
    }
}
