package com.eevee.billingservice.integration;

import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.billingservice.repository.BillingProcessedEventRepository;
import com.eevee.billingservice.repository.ProviderModelPriceRepository;
import com.eevee.billingservice.service.BillingRecordedService;
import com.eevee.usage.events.AiProvider;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Team-sourced usage must not land in personal {@code daily_expenditure_agg} / {@code monthly_expenditure_agg}
 * so monthly budget UI compares Identity budgets to personal-only spend.
 */
@SuppressWarnings("resource")
@SpringBootTest
class BillingRecordedTeamSkipsPersonalAggIntegrationTest extends AbstractBillingIntegrationTest {

    @Autowired
    private BillingRecordedService billingRecordedService;

    @Autowired
    private ProviderModelPriceRepository priceRepository;

    @Autowired
    private BillingProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void teamSource_writesTeamDailyAgg_notPersonalDaily() {
        Instant occurredAt = Instant.parse("2025-06-01T12:00:00Z");
        priceRepository.save(new ProviderModelPriceEntity(
                AiProvider.OPENAI,
                "gpt-4o-mini",
                Instant.parse("2020-01-01T00:00:00Z"),
                null,
                new BigDecimal("1"),
                new BigDecimal("2")
        ));

        UUID eventId = UUID.randomUUID();
        String userId = "user-team-agg-skip";
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                occurredAt,
                "corr-team-skip",
                userId,
                null,
                "7001",
                "ext-team-key",
                null,
                "88001",
                "fp-team",
                "team",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 1L, 2L, 3L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                true,
                200
        );

        billingRecordedService.process(event);

        assertThat(processedEventRepository.existsById(eventId)).isTrue();

        Long personalDailyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_expenditure_agg WHERE user_id = ?",
                Long.class,
                userId);
        assertThat(personalDailyCount).isZero();

        Long personalMonthlyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM monthly_expenditure_agg WHERE user_id = ?",
                Long.class,
                userId);
        assertThat(personalMonthlyCount).isZero();

        Long teamDailyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM team_api_key_daily_expenditure_agg WHERE team_api_key_id = ?",
                Long.class,
                88001L);
        assertThat(teamDailyCount).isEqualTo(1L);

        Long teamMonthlyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM team_api_key_monthly_expenditure_agg WHERE team_api_key_id = ?",
                Long.class,
                88001L);
        assertThat(teamMonthlyCount).isEqualTo(1L);
    }

    @Test
    @Transactional
    void nonTeamSource_still_writesPersonalDaily() {
        Instant occurredAt = Instant.parse("2025-06-02T12:00:00Z");
        priceRepository.save(new ProviderModelPriceEntity(
                AiProvider.OPENAI,
                "gpt-4o-mini",
                Instant.parse("2020-01-01T00:00:00Z"),
                null,
                new BigDecimal("1"),
                new BigDecimal("2")
        ));

        UUID eventId = UUID.randomUUID();
        String userId = "user-managed-agg";
        UsageRecordedEvent event = new UsageRecordedEvent(
                eventId,
                occurredAt,
                "corr-managed",
                userId,
                null,
                null,
                "key-managed",
                null,
                null,
                "cafebabedeadbeef",
                "managed",
                AiProvider.OPENAI,
                "gpt-4o-mini",
                new TokenUsage("gpt-4o-mini", 1L, 2L, 3L, null, null, null, null, null, null),
                BigDecimal.ZERO,
                "/proxy/openai/v1/chat/completions",
                "api.openai.com",
                false,
                true,
                200
        );

        billingRecordedService.process(event);

        assertThat(processedEventRepository.existsById(eventId)).isTrue();

        Long personalDailyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_expenditure_agg WHERE user_id = ?",
                Long.class,
                userId);
        assertThat(personalDailyCount).isEqualTo(1L);
    }
}
