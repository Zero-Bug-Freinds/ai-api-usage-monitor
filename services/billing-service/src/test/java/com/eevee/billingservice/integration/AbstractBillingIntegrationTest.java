package com.eevee.billingservice.integration;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

/**
 * Shared Testcontainers for billing integration tests.
 *
 * Keeping containers in a single static holder avoids repeated startup per test class,
 * which is a major contributor to CI runtime.
 */
@Testcontainers
abstract class AbstractBillingIntegrationTest {

    private static final Duration POSTGRES_STARTUP = Duration.ofMinutes(3);
    private static final Duration RABBIT_STARTUP = Duration.ofMinutes(2);

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine")
            .withStartupTimeout(RABBIT_STARTUP);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withStartupTimeout(POSTGRES_STARTUP)
            .waitingFor(
                    Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
                            .withStartupTimeout(POSTGRES_STARTUP));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.rabbitmq.host", rabbit::getHost);
        r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        r.add("spring.rabbitmq.username", () -> "guest");
        r.add("spring.rabbitmq.password", () -> "guest");
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.hikari.connection-timeout", () -> "120000");
        r.add("spring.datasource.hikari.validation-timeout", () -> "10000");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("billing.gateway.shared-secret", () -> "test-secret");
    }

    /**
     * Publishes when the broker accepts a connection; avoids failing the whole test on a single
     * {@link AmqpException} during CI cold starts (replaces long {@code AmqpAdmin} queue probes).
     */
    protected static void convertAndSendWhenReady(
            RabbitTemplate rabbitTemplate,
            String exchange,
            String routingKey,
            String body
    ) {
        await().atMost(45, SECONDS)
                .pollInterval(150, MILLISECONDS)
                .until(() -> {
                    try {
                        rabbitTemplate.convertAndSend(exchange, routingKey, body);
                        return true;
                    } catch (AmqpException e) {
                        return false;
                    }
                });
    }
}
