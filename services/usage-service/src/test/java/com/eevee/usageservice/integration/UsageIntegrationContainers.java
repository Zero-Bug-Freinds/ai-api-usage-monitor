package com.eevee.usageservice.integration;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Duration;

/**
 * CI에서 동시 Docker 부하 시 컨테이너 기동이 지연될 수 있어, 공통 기동 타임아웃을 넉넉히 둔다.
 */
public final class UsageIntegrationContainers {

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);

    private UsageIntegrationContainers() {
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
