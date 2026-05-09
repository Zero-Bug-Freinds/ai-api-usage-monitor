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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Budget-threshold spend uses {@link com.eevee.billingservice.service.BillingAggregationJdbc#sumDailyCostUsdForKstCalendarMonthAndProvider}
 * (per-provider KST calendar month), matching {@link com.eevee.billingservice.service.ExpenditureQueryService#summary}
 * for a full-month range. {@code monthly_expenditure_agg} remains all-provider for other features.
 */
@SuppressWarnings("resource")
@Tag("integration")
@SpringBootTest
@Import(IdentityBudgetClientMockConfig.class)
class ExpenditureSummaryVsBudgetThresholdNumeratorIntegrationTest extends AbstractBillingIntegrationTest {

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
