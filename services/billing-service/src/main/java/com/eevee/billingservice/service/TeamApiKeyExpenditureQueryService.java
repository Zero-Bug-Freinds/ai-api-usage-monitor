package com.eevee.billingservice.service;

import com.eevee.billingservice.api.dto.TeamApiKeyMonthSpend;
import com.eevee.billingservice.api.dto.TeamApiKeyMonthSpendResponse;
import com.eevee.billingservice.domain.BillingTeamApiKeyEntity;
import com.eevee.billingservice.repository.BillingTeamApiKeyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class TeamApiKeyExpenditureQueryService {

    private final BillingTeamApiKeyRepository teamApiKeyRepository;
    private final TeamApiKeyAggregationJdbc teamApiKeyAggregationJdbc;

    public TeamApiKeyExpenditureQueryService(
            BillingTeamApiKeyRepository teamApiKeyRepository,
            TeamApiKeyAggregationJdbc teamApiKeyAggregationJdbc
    ) {
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.teamApiKeyAggregationJdbc = teamApiKeyAggregationJdbc;
    }

    public TeamApiKeyMonthSpendResponse monthSpend(long teamId, LocalDate monthStartDate) {
        if (monthStartDate == null) {
            throw new IllegalArgumentException("monthStartDate is required");
        }
        if (monthStartDate.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("monthStartDate must be the first day of a month");
        }

        BigDecimal teamBudgetUsd = teamApiKeyAggregationJdbc.sumMonthlyBudgetUsdForTeam(teamId);
        BigDecimal teamSpendUsd = teamApiKeyAggregationJdbc.sumMonthlyCostUsdForTeam(monthStartDate, teamId);

        List<BillingTeamApiKeyEntity> keys = teamApiKeyRepository.findByTeamId(teamId);
        List<TeamApiKeyMonthSpend> rows = keys.stream()
                .map(k -> new TeamApiKeyMonthSpend(
                        k.getTeamApiKeyId(),
                        k.getAlias(),
                        k.getProvider(),
                        k.getMonthlyBudgetUsd(),
                        k.getStatus(),
                        teamApiKeyAggregationJdbc.sumMonthlyCostUsdForTeamApiKey(monthStartDate, k.getTeamApiKeyId())
                ))
                .sorted(Comparator
                        .comparing(TeamApiKeyMonthSpend::provider, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(TeamApiKeyMonthSpend::alias, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparingLong(TeamApiKeyMonthSpend::teamApiKeyId))
                .toList();

        return new TeamApiKeyMonthSpendResponse(teamId, monthStartDate, teamBudgetUsd, teamSpendUsd, rows);
    }
}

