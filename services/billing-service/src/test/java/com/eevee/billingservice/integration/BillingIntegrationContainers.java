package com.eevee.billingservice.integration;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Duration;

/**
 * Shared container factories for billing integration tests.
 * <p>
 * Mirrors the usage-service pattern: each test class owns its own {@link org.testcontainers.junit.jupiter.Container}
 * static fields built from these factories, paired with {@code @DirtiesContext(AFTER_CLASS)} on the abstract base.
 * Containers are stopped only after the Spring context has fully closed, so Hibernate's shutdown DDL and the AMQP
 * listener teardown never race with a dead broker/database — the failure mode that previously hung billing CI.
 */
public final class BillingIntegrationContainers {

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);

    private BillingIntegrationContainers() {
    }

    public static RabbitMQContainer rabbitMq() {
        return new RabbitMQContainer("rabbitmq:3.13-alpine")
                .withStartupTimeout(STARTUP_TIMEOUT);
    }

    public static PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("app")
                .withUsername("app")
                .withPassword("app")
                .withStartupTimeout(STARTUP_TIMEOUT);
    }
}
