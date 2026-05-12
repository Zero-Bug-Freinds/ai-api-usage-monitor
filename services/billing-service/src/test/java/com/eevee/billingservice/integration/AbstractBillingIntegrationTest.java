package com.eevee.billingservice.integration;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

/**
 * Shared Testcontainers for billing integration tests.
 * <p>
 * Singleton-container pattern: containers are started once in a static initializer and live for the
 * entire test JVM. JUnit's {@code @Container} lifecycle restarts the static fields between test
 * classes, which assigns a new random port each time; cached Spring contexts whose Hikari pool was
 * bound to the previous port then fail with {@code Connection refused} (postgres) on subsequent test
 * classes. Owning the lifecycle here keeps every {@link ServiceConnection}-derived context pointed
 * at a live container and, as a bonus, removes per-class container boot from the suite runtime.
 */
abstract class AbstractBillingIntegrationTest {

    private static final Duration POSTGRES_STARTUP = Duration.ofMinutes(2);
    private static final Duration RABBIT_STARTUP = Duration.ofMinutes(2);

    @ServiceConnection
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine")
            .withStartupTimeout(RABBIT_STARTUP);

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withStartupTimeout(POSTGRES_STARTUP);

    static {
        rabbit.start();
        postgres.start();
    }

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
