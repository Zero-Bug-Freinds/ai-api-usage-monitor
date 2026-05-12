package com.eevee.usageservice.integration;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyMetadataEntityId;
import com.eevee.usageservice.domain.ApiKeyMetadataScope;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same numeric {@code key_id} string for PERSONAL vs TEAM must remain two rows (composite PK + scope).
 * Requires Docker (same as other usage-service Testcontainers integration tests).
 */
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiKeyMetadataPersonalTeamIsolationIntegrationTest {

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
    private ApiKeyMetadataRepository repository;

    @Test
    void sameKeyIdString_personalAndTeam_rowsRemainSeparate() {
        Instant t = Instant.parse("2026-05-12T00:00:00Z");
        ApiKeyMetadataEntity personal = ApiKeyMetadataEntity.createPersonal("123", "user-a");
        personal.apply(null, "OPENAI", "p-alias", ApiKeyStatus.ACTIVE, t);
        ApiKeyMetadataEntity team = ApiKeyMetadataEntity.createTeamRow("123", "user-b", "7");
        team.apply("7", "OPENAI", "t-alias", ApiKeyStatus.ACTIVE, t);

        repository.save(personal);
        repository.save(team);

        var idPersonal = ApiKeyMetadataEntityId.personal("123", "user-a");
        var idTeam = ApiKeyMetadataEntityId.team("123", "user-b");

        assertThat(repository.findById(idPersonal)).isPresent();
        assertThat(repository.findById(idTeam)).isPresent();
        assertThat(repository.findById(idPersonal).orElseThrow().getKeyScope()).isEqualTo(ApiKeyMetadataScope.PERSONAL);
        assertThat(repository.findById(idTeam).orElseThrow().getKeyScope()).isEqualTo(ApiKeyMetadataScope.TEAM);
        assertThat(repository.findById(idPersonal).orElseThrow().getAlias()).isEqualTo("p-alias");
        assertThat(repository.findById(idTeam).orElseThrow().getAlias()).isEqualTo("t-alias");
    }
}
