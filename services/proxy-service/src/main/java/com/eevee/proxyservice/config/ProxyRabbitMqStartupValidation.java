package com.eevee.proxyservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Logs resolved RabbitMQ target at startup (usage.recorded publish). Surfaces misconfiguration when
 * docker profile still points at compose-only hostname {@code rabbitmq} on EC2.
 */
@Component
public class ProxyRabbitMqStartupValidation implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProxyRabbitMqStartupValidation.class);

    private final ConnectionFactory connectionFactory;
    private final Environment environment;
    private final ProxyProperties proxyProperties;

    public ProxyRabbitMqStartupValidation(
            ConnectionFactory connectionFactory,
            Environment environment,
            ProxyProperties proxyProperties
    ) {
        this.connectionFactory = connectionFactory;
        this.environment = environment;
        this.proxyProperties = proxyProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String host = environment.getProperty("spring.rabbitmq.host", "");
        int port = environment.getProperty("spring.rabbitmq.port", Integer.class, 5672);
        boolean ssl = environment.getProperty("spring.rabbitmq.ssl.enabled", Boolean.class, false);
        ProxyProperties.Rabbit rabbit = proxyProperties.getRabbit();
        log.info(
                "proxy RabbitMQ target host={} port={} ssl={} exchange={} routingKey={} connectionFactory={}",
                host,
                port,
                ssl,
                rabbit.getUsageExchange(),
                rabbit.getUsageRoutingKey(),
                connectionFactory.getClass().getSimpleName()
        );
        if (!StringUtils.hasText(host)) {
            log.error(
                    "spring.rabbitmq.host is blank — usage.recorded publish will fail; set SPRING_RABBITMQ_HOST "
                            + "(deploy: RABBITMQ_HOST in .env.deploy, e.g. host.docker.internal)"
            );
            return;
        }
        if (isDockerProfile() && "rabbitmq".equalsIgnoreCase(host.trim())) {
            log.warn(
                    "spring.rabbitmq.host is 'rabbitmq' under docker profile — OK for local Compose only; "
                            + "on EC2 set RABBITMQ_HOST=host.docker.internal in .env.deploy"
            );
        }
    }

    private boolean isDockerProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("docker".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
