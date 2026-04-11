package com.eevee.billingservice.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record TeamMonthRollupResponse(BigDecimal totalCostUsd, List<UserMonthCost> byUser) {
}
