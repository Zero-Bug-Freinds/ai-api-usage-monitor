package com.eevee.billingservice.api.dto;

import java.math.BigDecimal;

public record UserMonthCost(String userId, BigDecimal costUsd) {
}
