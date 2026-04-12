package com.eevee.billingservice.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamMonthRollupRequest(List<String> userIds, LocalDate monthStartDate) {
}
