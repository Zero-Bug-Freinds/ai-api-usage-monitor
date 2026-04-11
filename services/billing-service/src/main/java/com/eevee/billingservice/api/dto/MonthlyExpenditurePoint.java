package com.eevee.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MonthlyExpenditurePoint(
        LocalDate monthStartDate,
        BigDecimal costUsd,
        boolean finalized
) {
}
