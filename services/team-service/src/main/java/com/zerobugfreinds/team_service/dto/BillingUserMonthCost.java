package com.zerobugfreinds.team_service.dto;

import java.math.BigDecimal;

public record BillingUserMonthCost(String userId, BigDecimal costUsd) {
}
