package com.eevee.billingservice.service;

import com.eevee.billingservice.api.dto.TeamApiKeyMonthSpend;
import com.eevee.billingservice.api.dto.TeamApiKeyMonthSpendResponse;
import com.eevee.billingservice.domain.BillingTeamApiKeyEntity;
import com.eevee.billingservice.repository.BillingTeamApiKeyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class TeamApiKeyExpenditureQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** Matches team-service {@code TeamApiKeyStatus} names stored in {@code billing_team_api_key.status}. */
    private static boolean excludedFromSpendListing(String status) {
        return "DELETED".equals(status) || "DELETION_REQUESTED".equals(status);
    }

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
        return spend(teamId, monthStartDate, null, null);
    }

    public TeamApiKeyMonthSpendResponse spend(long teamId, LocalDate monthStartDate, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom;
        LocalDate effectiveTo;
        LocalDate effectiveMonthStartDate = null;

        if (from != null || to != null) {
            if (from == null || to == null) {
                throw new IllegalArgumentException("from and to must be provided together");
            }
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("to must be on or after from");
            }
            effectiveFrom = from;
            effectiveTo = to;
        } else if (monthStartDate != null) {
            if (monthStartDate.getDayOfMonth() != 1) {
                throw new IllegalArgumentException("monthStartDate must be the first day of a month");
            }
            effectiveMonthStartDate = monthStartDate;
            effectiveFrom = monthStartDate;
            effectiveTo = monthStartDate.plusMonths(1).minusDays(1);
        } else {
            effectiveMonthStartDate = LocalDate.now(KST).withDayOfMonth(1);
            effectiveFrom = effectiveMonthStartDate;
            effectiveTo = effectiveMonthStartDate.plusMonths(1).minusDays(1);
        }

        final LocalDate monthStartOrNull = effectiveMonthStartDate;
        BigDecimal teamBudgetUsd = teamApiKeyAggregationJdbc.sumMonthlyBudgetUsdForTeam(teamId);

        List<BillingTeamApiKeyEntity> keys = teamApiKeyRepository.findByTeamId(teamId).stream()
                .filter(k -> !excludedFromSpendListing(k.getStatus()))
                .toList();
        List<TeamApiKeyMonthSpend> rows = keys.stream()
                .map(k -> new TeamApiKeyMonthSpend(
                        k.getTeamApiKeyId(),
                        k.getAlias(),
                        k.getProvider(),
                        k.getMonthlyBudgetUsd(),
                        k.getStatus(),
                        resolveKeySpendUsd(k.getTeamApiKeyId(), effectiveFrom, effectiveTo, monthStartOrNull)
                ))
                .sorted(Comparator
                        .comparing(TeamApiKeyMonthSpend::provider, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(TeamApiKeyMonthSpend::alias, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparingLong(TeamApiKeyMonthSpend::teamApiKeyId))
                .toList();
        BigDecimal teamSpendUsd = rows.stream()
                .map(TeamApiKeyMonthSpend::monthSpendUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TeamApiKeyMonthSpendResponse(
                teamId,
                monthStartOrNull,
                effectiveFrom,
                effectiveTo,
                teamBudgetUsd,
                teamSpendUsd,
                rows
        );
    }

    private BigDecimal resolveKeySpendUsd(long teamApiKeyId, LocalDate from, LocalDate to, LocalDate monthStartDateOrNull) {
        if (monthStartDateOrNull != null && from.equals(monthStartDateOrNull) && to.equals(monthStartDateOrNull.plusMonths(1).minusDays(1))) {
            return teamApiKeyAggregationJdbc.sumMonthlyCostUsdForTeamApiKey(monthStartDateOrNull, teamApiKeyId);
        }
        return teamApiKeyAggregationJdbc.sumDailyCostUsdForTeamApiKey(from, to, teamApiKeyId);
    }
}

