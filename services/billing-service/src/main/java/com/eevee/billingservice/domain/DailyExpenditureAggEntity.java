package com.eevee.billingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "daily_expenditure_agg")
public class DailyExpenditureAggEntity {

    @EmbeddedId
    private DailyExpenditureAggId id;

    @Column(name = "total_cost_usd", nullable = false, precision = 24, scale = 10)
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    @Column(name = "total_prompt_tokens", nullable = false)
    private long totalPromptTokens;

    @Column(name = "total_completion_tokens", nullable = false)
    private long totalCompletionTokens;

    protected DailyExpenditureAggEntity() {
    }

    public DailyExpenditureAggEntity(DailyExpenditureAggId id, BigDecimal totalCostUsd, long totalPromptTokens, long totalCompletionTokens) {
        this.id = id;
        this.totalCostUsd = totalCostUsd;
        this.totalPromptTokens = totalPromptTokens;
        this.totalCompletionTokens = totalCompletionTokens;
    }

    public DailyExpenditureAggId getId() {
        return id;
    }

    public BigDecimal getTotalCostUsd() {
        return totalCostUsd;
    }

    public long getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public long getTotalCompletionTokens() {
        return totalCompletionTokens;
    }
}
