package com.eevee.billingservice.service;

import com.eevee.billingservice.api.dto.BudgetAuthorityScope;
import com.eevee.billingservice.api.dto.MonthlyBudgetAuthorityResponse;
import com.eevee.billingservice.integration.IdentityBudgetClient;
import com.eevee.billingservice.integration.IdentityMonthlyBudgetEnvelope;
import com.eevee.usage.events.AiProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase A: exposes Identity-composed budgets as the single effective authority (no plan/team caps yet).
 */
@Service
public class BudgetAuthorityService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IdentityBudgetClient identityBudgetClient;

    public BudgetAuthorityService(IdentityBudgetClient identityBudgetClient) {
        this.identityBudgetClient = identityBudgetClient;
    }

    public MonthlyBudgetAuthorityResponse resolve(
            String userId,
            BudgetAuthorityScope scope,
            AiProvider provider,
            String apiKeyId,
            LocalDate month
    ) {
        if (month != null && month.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("month must be the first day of a calendar month (YYYY-MM-01).");
        }
        Instant computedAt = Instant.now();
        LocalDate effectiveMonth = month != null ? month : LocalDate.now(KST).withDayOfMonth(1);
        List<String> notes = new ArrayList<>();
        notes.add("Phase A: effective value mirrors Identity envelope (no plan/team caps merged in billing yet).");
        if (month == null) {
            notes.add("month omitted: month field defaults to the first day of the current calendar month in the billing service system default zone (Identity call is unchanged in Phase A).");
        }

        Optional<IdentityMonthlyBudgetEnvelope> env = identityBudgetClient.fetchMonthlyBudgetEnvelope(userId);
        if (env.isEmpty()) {
            notes.add("Identity budget envelope unavailable (disabled, blank config, parse error, or HTTP not successful).");
            return new MonthlyBudgetAuthorityResponse(
                    scope,
                    effectiveMonth,
                    null,
                    null,
                    null,
                    notes,
                    computedAt
            );
        }

        IdentityMonthlyBudgetEnvelope envelope = env.get();
        BigDecimal identityUserTotal = envelope.monthlyBudgetUsd();

        if (scope == BudgetAuthorityScope.USER) {
            notes.add("USER scope: effectiveMonthlyBudgetUsd = Identity root monthlyBudgetUsd when present.");
            return new MonthlyBudgetAuthorityResponse(
                    scope,
                    effectiveMonth,
                    identityUserTotal,
                    identityUserTotal,
                    null,
                    notes,
                    computedAt
            );
        }

        notes.add("API_KEY scope: effectiveMonthlyBudgetUsd matches Identity per-key row when apiKeyId parses to external id and provider matches.");
        if (provider == null || apiKeyId == null || apiKeyId.isBlank()) {
            notes.add("API_KEY scope requires provider and apiKeyId query parameters.");
            return new MonthlyBudgetAuthorityResponse(
                    scope,
                    effectiveMonth,
                    null,
                    identityUserTotal,
                    null,
                    notes,
                    computedAt
            );
        }

        Optional<BigDecimal> keyUsd = identityBudgetClient.fetchMonthlyBudgetUsdForKey(userId, provider, apiKeyId);
        if (keyUsd.isEmpty()) {
            notes.add("No matching Identity per-key budget row for (provider, apiKeyId), or apiKeyId is not a numeric external key id.");
            return new MonthlyBudgetAuthorityResponse(
                    scope,
                    effectiveMonth,
                    null,
                    identityUserTotal,
                    null,
                    notes,
                    computedAt
            );
        }

        BigDecimal effective = keyUsd.get();
        notes.add("Matched Identity monthlyBudgetsByKey entry for provider=" + provider.name() + ".");
        return new MonthlyBudgetAuthorityResponse(
                scope,
                effectiveMonth,
                effective,
                identityUserTotal,
                effective,
                notes,
                computedAt
        );
    }
}
