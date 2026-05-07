package com.eevee.billingservice.integration;

import com.eevee.billingservice.api.dto.ExpenditureSummaryResponse;
import com.eevee.billingservice.domain.DailyExpenditureAggEntity;
import com.eevee.billingservice.domain.DailyExpenditureAggId;
import com.eevee.billingservice.domain.MonthlyExpenditureAggEntity;
import com.eevee.billingservice.domain.MonthlyExpenditureAggId;
import com.eevee.billingservice.repository.DailyExpenditureAggRepository;
import com.eevee.billingservice.repository.MonthlyExpenditureAggRepository;
import com.eevee.billingservice.service.BillingAggregationJdbc;
import com.eevee.billingservice.service.ExpenditureQueryService;
import com.eevee.usage.events.AiProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Budget-threshold spend uses {@link com.eevee.billingservice.service.BillingAggregationJdbc#sumDailyCostUsdForKstCalendarMonthAndProvider}
 * (per-provider KST calendar month), matching {@link com.eevee.billingservice.service.ExpenditureQueryService#summary}
 * for a full-month range. {@code monthly_expenditure_agg} remains all-provider for other features.
 */
@SuppressWarnings("resource")
@SpringBootTest
@Import(IdentityBudgetClientMockConfig.class)
@Testcontainers
class ExpenditureSummaryVsBudgetThresholdNumeratorIntegrationTest {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app");

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

    @Autowired
    private DailyExpenditureAggRepository dailyRepository;

    @Autowired
    private MonthlyExpenditureAggRepository monthlyRepository;

    @Autowired
    private ExpenditureQueryService expenditureQueryService;

    @Autowired
    private BillingAggregationJdbc billingAggregationJdbc;

    @Test
    @Transactional
    void openAiSummaryTotal_matchesProviderScopedDailyMonthSum_notAllProviderMonthlyAgg() {
        String userId = "user-mismatch";
        String apiKeyId = "7";
        LocalDate monthStart = LocalDate.of(2026, 5, 1);
        LocalDate aggDay = LocalDate.of(2026, 5, 2);

        dailyRepository.save(new DailyExpenditureAggEntity(
                new DailyExpenditureAggId(aggDay, userId, apiKeyId, AiProvider.OPENAI, "gpt-4o-mini"),
                new BigDecimal("0.0100"),
                1L,
                1L
        ));
        dailyRepository.save(new DailyExpenditureAggEntity(
                new DailyExpenditureAggId(aggDay, userId, apiKeyId, AiProvider.GOOGLE, "gemini-2.5-flash"),
                new BigDecimal("0.0100"),
                1L,
                1L
        ));

        monthlyRepository.save(new MonthlyExpenditureAggEntity(
                new MonthlyExpenditureAggId(monthStart, userId, apiKeyId),
                new BigDecimal("0.0200"),
                false,
                null
        ));

        ExpenditureSummaryResponse summary = expenditureQueryService.summary(
                userId,
                apiKeyId,
                AiProvider.OPENAI,
                monthStart,
                aggDay
        );

        BigDecimal allProviderMonthlyAgg = billingAggregationJdbc.findMonthlyTotalUsd(monthStart, userId, apiKeyId);
        BigDecimal openAiDailyMonthSum = billingAggregationJdbc.sumDailyCostUsdForKstCalendarMonthAndProvider(
                monthStart,
                userId,
                apiKeyId,
                AiProvider.OPENAI
        );

        assertThat(summary.totalCostUsd()).isEqualByComparingTo("0.01");
        assertThat(openAiDailyMonthSum).isEqualByComparingTo("0.01");
        assertThat(allProviderMonthlyAgg).isEqualByComparingTo("0.02");
    }
}
