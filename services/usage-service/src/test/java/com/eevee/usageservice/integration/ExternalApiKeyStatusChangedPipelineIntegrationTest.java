package com.eevee.usageservice.integration;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.domain.ApiKeyMetadataEntityId;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatus;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
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

    private static final String USER_101 = "user101@integration.test";

    private static final String USER_303 = "user303@integration.test";

    private static final String USER_778 = "user778@integration.test";

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
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
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
        ExternalApiKeyStatusChangedEvent registered = ExternalApiKeyStatusChangedEvent.of(
                101L,
                "GoogleTestKey1",
                USER_101,
                "GOOGLE",
                ExternalApiKeyStatus.ACTIVE,
                "kh"
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(registered)
        );

        var id101 = ApiKeyMetadataEntityId.personal("101", USER_101);
        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> repository.findById(id101).isPresent());

        var active = repository.findById(id101).orElseThrow();
        assertThat(active.getAlias()).isEqualTo("GoogleTestKey1");
        assertThat(active.getStatus().name()).isEqualTo("ACTIVE");

        ExternalApiKeyStatusChangedEvent aliasUpdated = ExternalApiKeyStatusChangedEvent.of(
                101L,
                "GoogleTestKey1-Renamed",
                USER_101,
                "GOOGLE",
                ExternalApiKeyStatus.ACTIVE,
                "kh"
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(aliasUpdated)
        );

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var updated = repository.findById(id101).orElseThrow();
                    assertThat(updated.getAlias()).isEqualTo("GoogleTestKey1-Renamed");
                    assertThat(updated.getStatus().name()).isEqualTo("ACTIVE");
                });

        ExternalApiKeyDeletedEvent deleted = ExternalApiKeyDeletedEvent.of(
                USER_101,
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
                    var deletedRow = repository.findById(id101).orElseThrow();
                    assertThat(deletedRow.getAlias()).isEqualTo("GoogleTestKey1-Renamed");
                    assertThat(deletedRow.getStatus().name()).isEqualTo("DELETED");
                });
    }

    @Test
    void deletedEvent_retainLogsFalse_removesUsageLogsAndMetadata() throws Exception {
        ExternalApiKeyStatusChangedEvent registered = ExternalApiKeyStatusChangedEvent.of(
                303L,
                "KeyToPurge",
                USER_303,
                "GOOGLE",
                ExternalApiKeyStatus.ACTIVE,
                "kh"
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(registered)
        );
        var id303 = ApiKeyMetadataEntityId.personal("303", USER_303);
        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> repository.findById(id303).isPresent());

        UsageRecordedLogEntity log = new UsageRecordedLogEntity(
                UUID.randomUUID(),
                Instant.parse("2026-04-16T09:00:00Z"),
                null,
                USER_303,
                null,
                null,
                "303",
                null,
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

        ExternalApiKeyDeletedEvent purge = ExternalApiKeyDeletedEvent.of(
                USER_303,
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
                    assertThat(repository.findById(id303)).isEmpty();
                    assertThat(usageRecordedLogRepository.countByApiKeyId("303")).isEqualTo(0L);
                });
    }

    @Test
    void budgetChangedEvent_doesNotCreateOrAlterApiKeyMetadata() throws Exception {
        String budgetJson = """
                {
                  "eventType": "EXTERNAL_API_KEY_BUDGET_CHANGED",
                  "schemaVersion": 2,
                  "occurredAt": "2026-05-11T10:00:00Z",
                  "keyId": 777,
                  "alias": "BudgetPayloadAlias",
                  "userId": "user-42@integration.test",
                  "visibility": "PRIVATE",
                  "provider": "GOOGLE",
                  "status": "ACTIVE",
                  "monthlyBudgetUsd": 12.50,
                  "keyHash": "hash-budget"
                }
                """;
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                budgetJson
        );

        ExternalApiKeyStatusChangedEvent registered = ExternalApiKeyStatusChangedEvent.of(
                778L,
                "AfterBudgetControl",
                USER_778,
                "OPENAI",
                ExternalApiKeyStatus.ACTIVE,
                "kh"
        );
        rabbitTemplate.convertAndSend(
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(registered)
        );

        var id778 = ApiKeyMetadataEntityId.personal("778", USER_778);
        await().atMost(30, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> repository.findById(id778).isPresent());

        assertThat(repository.findById(ApiKeyMetadataEntityId.personal("777", "user-42@integration.test"))).isEmpty();
    }
}
