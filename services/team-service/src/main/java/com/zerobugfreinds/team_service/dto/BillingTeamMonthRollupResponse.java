package com.zerobugfreinds.team_service.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * billing-service {@code TeamMonthRollupResponse} JSON 과 호환되는 응답 DTO.
 */
public record BillingTeamMonthRollupResponse(BigDecimal totalCostUsd, List<BillingUserMonthCost> byUser) {
}
