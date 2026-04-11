package com.eevee.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyExpenditurePoint(LocalDate date, BigDecimal costUsd) {
}
