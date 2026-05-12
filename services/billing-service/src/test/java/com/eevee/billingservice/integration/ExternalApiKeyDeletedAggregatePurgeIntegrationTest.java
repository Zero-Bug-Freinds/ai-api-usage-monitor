package com.eevee.billingservice.integration;

import com.eevee.billingservice.service.BillingAggregationJdbc;
import com.eevee.usage.events.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("resource")
@SpringBootTest
class ExternalApiKeyDeletedAggregatePurgeIntegrationTest extends AbstractBillingIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BillingAggregationJdbc billingAggregationJdbc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long countPersonalAggRows(String userIdStored, String apiKeyId) {
        Long d = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM daily_expenditure_agg
                        WHERE user_id = ? AND api_key_id = ?
                        """,
                Long.class,
                userIdStored,
                apiKeyId
        );
        Long m = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM monthly_expenditure_agg
                        WHERE user_id = ? AND api_key_id = ?
                        """,
                Long.class,
                userIdStored,
                apiKeyId
        );
        Long s = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM billing_user_api_key_seen
                        WHERE user_id = ? AND api_key_id = ?
                        """,
                Long.class,
                userIdStored,
                apiKeyId
        );
        return d + m + s;
    }

    @Test
    void externalApiKeyDeletedEvent_removesDailyMonthlyAndSeenForMatchingUser() throws Exception {
        String storedUserId = "user@test.com";
        String apiKeyId = "55";
        LocalDate day = LocalDate.of(2026, 5, 10);
        LocalDate monthStart = LocalDate.of(2026, 5, 1);

        billingAggregationJdbc.upsertDaily(
                day,
                storedUserId,
                apiKeyId,
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new BigDecimal("0.02"),
                1L,
                1L
        );
        billingAggregationJdbc.upsertMonthly(monthStart, storedUserId, apiKeyId, new BigDecimal("0.02"));
        billingAggregationJdbc.upsertSeen(storedUserId, apiKeyId, AiProvider.OPENAI, Instant.parse("2026-05-10T00:00:00Z"));

        assertThat(countPersonalAggRows(storedUserId, apiKeyId)).isEqualTo(3L);

        ExternalApiKeyDeletedEvent ev = ExternalApiKeyDeletedEvent.of(
                "  USER@test.COM ",
                55L,
                Instant.parse("2026-05-10T12:00:00Z"),
                false,
                "OPENAI",
                "alias"
        );

        convertAndSendWhenReady(
                rabbitTemplate,
                "identity.events",
                "identity.external-api-key.status-changed",
                objectMapper.writeValueAsString(ev)
        );

        await().atMost(45, SECONDS).pollInterval(100, MILLISECONDS)
                .until(() -> countPersonalAggRows(storedUserId, apiKeyId) == 0L);
    }

    @Test
    void budgetChangedEvent_doesNotRemoveAggregates() {
        String storedUserId = "keep@example.com";
        String apiKeyId = "77";
        LocalDate day = LocalDate.of(2026, 5, 11);
        billingAggregationJdbc.upsertDaily(
                day,
                storedUserId,
                apiKeyId,
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new BigDecimal("0.01"),
                0L,
                0L
        );

        long before = countPersonalAggRows(storedUserId, apiKeyId);

        String json = "{\"eventType\":\"EXTERNAL_API_KEY_BUDGET_CHANGED\",\"userId\":\"" + storedUserId + "\",\"apiKeyId\":77}";

        convertAndSendWhenReady(
                rabbitTemplate,
                "identity.events",
                "identity.external-api-key.status-changed",
                json
        );

        await().atMost(10, SECONDS).pollInterval(200, MILLISECONDS)
                .untilAsserted(() -> assertThat(countPersonalAggRows(storedUserId, apiKeyId)).isEqualTo(before));
    }
}
