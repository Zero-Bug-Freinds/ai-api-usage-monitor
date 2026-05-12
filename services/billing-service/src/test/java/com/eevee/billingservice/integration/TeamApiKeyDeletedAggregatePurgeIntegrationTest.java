package com.eevee.billingservice.integration;

import com.eevee.billingservice.domain.BillingTeamApiKeyEntity;
import com.eevee.billingservice.repository.BillingTeamApiKeyRepository;
import com.eevee.billingservice.service.TeamApiKeyAggregationJdbc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("resource")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TeamApiKeyDeletedAggregatePurgeIntegrationTest extends AbstractBillingIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeamApiKeyAggregationJdbc teamApiKeyAggregationJdbc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private BillingTeamApiKeyRepository billingTeamApiKeyRepository;

    @Value("${billing.rabbit.team-domain-in.queue}")
    private String teamDomainQueue;

    @BeforeEach
    void waitForQueue() {
        await().atMost(20, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> amqpAdmin.getQueueProperties(teamDomainQueue) != null);
    }

    private long countTeamAggRows(long teamApiKeyId) {
        Long d = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM team_api_key_daily_expenditure_agg
                        WHERE team_api_key_id = ?
                        """,
                Long.class,
                teamApiKeyId
        );
        Long m = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM team_api_key_monthly_expenditure_agg
                        WHERE team_api_key_id = ?
                        """,
                Long.class,
                teamApiKeyId
        );
        return d + m;
    }

    @Test
    void teamApiKeyDeletedEvent_removesAggregatesAndReadModelRow() throws Exception {
        long teamApiKeyId = 99001L;
        long teamId = 7001L;
        LocalDate day = LocalDate.of(2026, 5, 10);
        LocalDate monthStart = LocalDate.of(2026, 5, 1);

        Instant now = Instant.parse("2026-05-10T12:00:00Z");
        billingTeamApiKeyRepository.save(new BillingTeamApiKeyEntity(
                teamApiKeyId,
                teamId,
                "alias-a",
                "OPENAI",
                new BigDecimal("10.000000"),
                "ACTIVE",
                false,
                now,
                now
        ));

        teamApiKeyAggregationJdbc.upsertDaily(day, teamApiKeyId, new BigDecimal("0.03"));
        teamApiKeyAggregationJdbc.upsertMonthly(monthStart, teamApiKeyId, new BigDecimal("0.03"));

        assertThat(countTeamAggRows(teamApiKeyId)).isEqualTo(2L);
        assertThat(billingTeamApiKeyRepository.findById(teamApiKeyId)).isPresent();

        String json = objectMapper.writeValueAsString(Map.of(
                "eventType", "TEAM_API_KEY_DELETED",
                "teamId", String.valueOf(teamId),
                "teamName", "t",
                "actorUserId", "owner",
                "occurredAt", now,
                "recipientUserIds", java.util.List.of(),
                "apiKeyId", teamApiKeyId,
                "retainLogs", false,
                "provider", "OPENAI",
                "alias", "alias-a"
        ));

        rabbitTemplate.convertAndSend(
                "team.events",
                "team.api.key.deleted",
                json
        );

        await().atMost(30, SECONDS).pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .until(() -> countTeamAggRows(teamApiKeyId) == 0L
                        && billingTeamApiKeyRepository.findById(teamApiKeyId).isEmpty());
    }

    @Test
    void registeredEvent_doesNotRemoveAggregates() throws Exception {
        long teamApiKeyId = 99002L;
        LocalDate day = LocalDate.of(2026, 5, 12);

        teamApiKeyAggregationJdbc.upsertDaily(day, teamApiKeyId, new BigDecimal("0.01"));

        long before = countTeamAggRows(teamApiKeyId);

        String json = objectMapper.writeValueAsString(Map.of(
                "eventType", "TEAM_API_KEY_REGISTERED",
                "teamId", "1",
                "teamName", "n",
                "actorUserId", "a",
                "occurredAt", Instant.parse("2026-05-12T12:00:00Z"),
                "recipientUserIds", java.util.List.of(),
                "apiKeyId", teamApiKeyId,
                "provider", "OPENAI",
                "alias", "x"
        ));

        rabbitTemplate.convertAndSend(
                "team.events",
                "team.api.key.registered",
                json
        );

        await().atMost(5, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(countTeamAggRows(teamApiKeyId)).isEqualTo(before));
    }
}
