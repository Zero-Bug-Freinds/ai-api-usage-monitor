package com.eevee.usageservice.integration;

import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * proxy-service가 {@code RabbitTemplate.convertAndSend(exchange, routingKey, jsonString)} 한 것과
 * 동일한 방식으로 메시지를 넣으면, 리스너가 역직렬화·저장까지 수행하는지 검증한다.
 * <p>
 * Docker가 필요하다. 로컬에서 Docker 없이 빌드할 때는 {@code ./gradlew test -x integration} 등으로
 * 제외하거나, 이 클래스만 비활성화할 수 있다.
 */
@SpringBootTest
@Testcontainers
class UsageRecordedEventPipelineIntegrationTest {

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
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsageRecordedLogRepository repository;

    @Test
    void jsonPublishedLikeProxy_isConsumedAndPersisted() throws Exception {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                Instant.parse("2025-06-01T12:00:00Z"),
                "corr-it",
                "user-it",
                null,
                null,
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 1L, 2L, 3L),
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
                .until(() -> repository.existsByEventId(eventId));

        assertThat(repository.findById(eventId)).isPresent();
    }
}
