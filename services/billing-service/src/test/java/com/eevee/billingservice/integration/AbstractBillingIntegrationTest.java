package com.eevee.billingservice.integration;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
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
 *
 * Keeping containers in a single static holder avoids repeated startup per test class,
 * which is a major contributor to CI runtime.
 */
@Testcontainers
abstract class AbstractBillingIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withStartupTimeout(Duration.ofMinutes(2));

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

    /**
     * Waits until {@link AmqpAdmin#getQueueProperties(String)} reports the queue exists.
     * Awaitility's default {@code until} fails the whole wait if the predicate throws; during CI cold
     * starts the first AMQP calls can throw {@link AmqpException} while the broker socket is not ready.
     */
    protected static void awaitQueuePresent(AmqpAdmin amqpAdmin, String queueName, long timeoutSeconds) {
        await().atMost(timeoutSeconds, SECONDS)
                .pollInterval(200, MILLISECONDS)
                .until(() -> {
                    try {
                        return amqpAdmin.getQueueProperties(queueName) != null;
                    } catch (AmqpException e) {
                        return false;
                    }
                });
    }
}
