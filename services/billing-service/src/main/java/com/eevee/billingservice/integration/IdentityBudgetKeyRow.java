package com.eevee.billingservice.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentityBudgetKeyRow(Long externalApiKeyId, String provider, String alias, BigDecimal monthlyBudgetUsd) {
}
