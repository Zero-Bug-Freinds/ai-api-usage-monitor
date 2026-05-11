package com.zerobugfreinds.team_service.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * billing-service {@code POST /api/v1/expenditure/team/month-rollup} 요청 본문과 동일한 형태.
 */
public record BillingTeamMonthRollupRequest(List<String> userIds, LocalDate monthStartDate) {
}
