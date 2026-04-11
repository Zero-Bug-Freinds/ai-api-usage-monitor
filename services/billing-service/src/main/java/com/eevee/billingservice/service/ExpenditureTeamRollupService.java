package com.eevee.billingservice.service;

import com.eevee.billingservice.api.dto.TeamMonthRollupRequest;
import com.eevee.billingservice.api.dto.TeamMonthRollupResponse;
import com.eevee.billingservice.api.dto.UserMonthCost;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sums {@code monthly_expenditure_agg} across API keys for many users (team rollup).
 */
@Service
public class ExpenditureTeamRollupService {

    private static final int MAX_USER_IDS = 500;

    private final JdbcTemplate jdbcTemplate;

    public ExpenditureTeamRollupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TeamMonthRollupResponse rollup(TeamMonthRollupRequest request) {
        LocalDate monthStart = request.monthStartDate();
        if (monthStart == null) {
            throw new IllegalArgumentException("monthStartDate is required");
        }
        if (monthStart.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("monthStartDate must be the first day of a month");
        }
        List<String> userIds = request.userIds() == null ? List.of() : request.userIds().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (userIds.size() > MAX_USER_IDS) {
            throw new IllegalArgumentException("too many userIds (max " + MAX_USER_IDS + ")");
        }
        if (userIds.isEmpty()) {
            return new TeamMonthRollupResponse(BigDecimal.ZERO, List.of());
        }

        StringBuilder sql = new StringBuilder(
                """
                        SELECT user_id, COALESCE(SUM(total_cost_usd), 0)
                        FROM monthly_expenditure_agg
                        WHERE month_start_date = ?
                        AND user_id IN (
                        """
        );
        sql.append(String.join(",", userIds.stream().map(u -> "?").toList()));
        sql.append(") GROUP BY user_id ORDER BY user_id");

        List<Object> args = new ArrayList<>();
        args.add(monthStart);
        args.addAll(userIds);

        List<UserMonthCost> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new UserMonthCost(
                        rs.getString(1),
                        rs.getBigDecimal(2)
                ),
                args.toArray()
        );

        BigDecimal total = rows.stream()
                .map(UserMonthCost::costUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TeamMonthRollupResponse(total, rows);
    }
}
