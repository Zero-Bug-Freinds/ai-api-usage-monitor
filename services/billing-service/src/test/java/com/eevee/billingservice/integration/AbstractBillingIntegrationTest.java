package com.eevee.billingservice.integration;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

/**
 * Shared Testcontainers + Spring properties for billing integration tests.
 * <p>
 * Mirrors {@code usage-service} integration test infrastructure to avoid the JVM-shutdown hang previously seen in
 * billing CI: {@link DirtiesContext} closes the Spring context immediately after each test class — while the
 * containers are still alive — so Hibernate and the AMQP listeners shut down cleanly. {@code ddl-auto: update}
 * lets Hibernate create the tables Flyway migrations do not yet cover (production runs the same mode) and, unlike
 * {@code create-drop}, never issues DROP statements that would block on a dying container during JVM shutdown.
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractBillingIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = BillingIntegrationContainers.rabbitMq();

    @Container
    static final PostgreSQLContainer<?> postgres = BillingIntegrationContainers.postgres();

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.rabbitmq.host", rabbit::getHost);
        r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        r.add("spring.rabbitmq.username", () -> "guest");
        r.add("spring.rabbitmq.password", () -> "guest");
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("billing.gateway.shared-secret", () -> "test-secret");
    }

    /**
     * Publishes when the broker accepts a connection; absorbs the short window between context bootstrap and the
     * AMQP listener containers reaching {@code attemptDeclarations} during cold starts.
     */
    protected static void convertAndSendWhenReady(
            RabbitTemplate rabbitTemplate,
            String exchange,
            String routingKey,
            String body
    ) {
        await().atMost(30, SECONDS)
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
