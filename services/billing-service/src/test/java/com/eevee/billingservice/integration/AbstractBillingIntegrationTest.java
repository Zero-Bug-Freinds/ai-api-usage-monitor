package com.eevee.billingservice.integration;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

/**
 * Shared Testcontainers for billing integration tests.
 * <p>
 * {@link ServiceConnection} ties container lifecycle to the Spring test context so services start
 * before auto-configured beans and stop only after context teardown — avoiding JDBC/AMQP
 * {@code Connection refused} races seen with {@code @DynamicPropertySource} + JUnit-only lifecycle.
 */
@Testcontainers
abstract class AbstractBillingIntegrationTest {

    private static final Duration POSTGRES_STARTUP = Duration.ofMinutes(2);
    private static final Duration RABBIT_STARTUP = Duration.ofMinutes(2);

    @Container
    @ServiceConnection
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine")
            .withStartupTimeout(RABBIT_STARTUP);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withStartupTimeout(POSTGRES_STARTUP);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("billing.gateway.shared-secret", () -> "test-secret");
    }

    /**
     * Publishes when the broker accepts a connection; avoids failing the whole test on a single
     * {@link AmqpException} during CI cold starts.
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
