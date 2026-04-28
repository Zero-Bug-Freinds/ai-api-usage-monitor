package com.eevee.billingservice.service;

import com.eevee.billingservice.api.dto.ApiKeySeenResponse;
import com.eevee.billingservice.api.dto.DailyExpenditurePoint;
import com.eevee.billingservice.api.dto.ExpenditureSummaryResponse;
import com.eevee.billingservice.api.dto.MonthlyBudgetStatusResponse;
import com.eevee.billingservice.api.dto.MonthlyExpenditurePoint;
import com.eevee.billingservice.config.BillingProperties;
import com.eevee.billingservice.domain.BillingUserApiKeySeenEntity;
import com.eevee.billingservice.domain.MonthlyExpenditureAggEntity;
import com.eevee.billingservice.integration.IdentityBudgetClient;
import com.eevee.billingservice.repository.BillingUserApiKeySeenRepository;
import com.eevee.billingservice.repository.DailyExpenditureAggRepository;
import com.eevee.billingservice.repository.MonthlyExpenditureAggRepository;
import com.eevee.usage.events.AiProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExpenditureQueryService {

    private final DailyExpenditureAggRepository dailyRepository;
    private final MonthlyExpenditureAggRepository monthlyRepository;
    private final BillingUserApiKeySeenRepository seenRepository;
    private final IdentityBudgetClient identityBudgetClient;
    private final BillingProperties billingProperties;

    public ExpenditureQueryService(
            DailyExpenditureAggRepository dailyRepository,
            MonthlyExpenditureAggRepository monthlyRepository,
            BillingUserApiKeySeenRepository seenRepository,
            IdentityBudgetClient identityBudgetClient,
            BillingProperties billingProperties
    ) {
        this.dailyRepository = dailyRepository;
        this.monthlyRepository = monthlyRepository;
        this.seenRepository = seenRepository;
        this.identityBudgetClient = identityBudgetClient;
        this.billingProperties = billingProperties;
    }

    @Transactional(readOnly = true)
    public ExpenditureSummaryResponse summary(String userId, String apiKeyId, AiProvider provider, LocalDate from, LocalDate to) {
        Range r = validateRange(from, to);
        BigDecimal total = dailyRepository.sumTotalCostUsd(userId, apiKeyId, provider, r.fromInclusive(), r.toInclusive());
        BigDecimal budget = identityBudgetClient.fetchMonthlyBudgetUsdForKey(userId, provider, apiKeyId).orElse(null);
        return new ExpenditureSummaryResponse(r.fromInclusive(), r.toInclusive(), total, budget);
    }

    @Transactional(readOnly = true)
    public MonthlyBudgetStatusResponse monthlyBudgetStatus(String userId, LocalDate from, LocalDate to) {
        Range r = validateRange(from, to);
        BigDecimal total = dailyRepository.sumTotalCostUsdForUser(userId, r.fromInclusive(), r.toInclusive());
        BigDecimal budget = identityBudgetClient.fetchMonthlyBudgetUsd(userId).orElse(null);
        return new MonthlyBudgetStatusResponse(r.fromInclusive(), r.toInclusive(), total, budget);
    }

    @Transactional(readOnly = true)
    public List<DailyExpenditurePoint> dailySeries(String userId, String apiKeyId, AiProvider provider, LocalDate from, LocalDate to) {
        Range r = validateRange(from, to);
        List<Object[]> rows = dailyRepository.sumCostGroupedByDay(userId, apiKeyId, provider, r.fromInclusive(), r.toInclusive());
        List<DailyExpenditurePoint> out = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate d = (LocalDate) row[0];
            BigDecimal cost = (BigDecimal) row[1];
            out.add(new DailyExpenditurePoint(d, cost));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<MonthlyExpenditurePoint> monthlySeries(String userId, String apiKeyId, LocalDate from, LocalDate to) {
        Range r = validateRange(from, to);
        LocalDate fromMonth = r.fromInclusive().withDayOfMonth(1);
        LocalDate toMonth = r.toInclusive().withDayOfMonth(1);
        List<MonthlyExpenditureAggEntity> rows = monthlyRepository.findSeries(userId, apiKeyId, fromMonth, toMonth);
        List<MonthlyExpenditurePoint> out = new ArrayList<>();
        for (MonthlyExpenditureAggEntity m : rows) {
            out.add(new MonthlyExpenditurePoint(
                    m.getId().getMonthStartDate(),
                    m.getTotalCostUsd(),
                    m.isFinalized()
            ));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ApiKeySeenResponse> listApiKeys(String userId, AiProvider providerOrNull) {
        List<BillingUserApiKeySeenEntity> rows = providerOrNull == null
                ? seenRepository.findByIdUserIdOrderByIdApiKeyIdAsc(userId)
                : seenRepository.findByIdUserIdAndIdProviderOrderByIdApiKeyIdAsc(userId, providerOrNull);
        List<ApiKeySeenResponse> out = new ArrayList<>();
        for (BillingUserApiKeySeenEntity e : rows) {
            out.add(new ApiKeySeenResponse(
                    e.getId().getApiKeyId(),
                    e.getId().getProvider(),
                    e.getFirstSeenAt()
            ));
        }
        return out;
    }

    private Range validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("invalid range: to before from");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        int max = billingProperties.getAnalytics().getMaxRangeDays();
        if (days > max) {
            throw new IllegalArgumentException("range too large (max " + max + " days)");
        }
        return new Range(from, to);
    }

    private record Range(LocalDate fromInclusive, LocalDate toInclusive) {
    }
}
