package com.eevee.billingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "monthly_expenditure_agg")
public class MonthlyExpenditureAggEntity {

    @EmbeddedId
    private MonthlyExpenditureAggId id;

    @Column(name = "total_cost_usd", nullable = false, precision = 24, scale = 10)
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    @Column(name = "is_finalized", nullable = false)
    private boolean finalized;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected MonthlyExpenditureAggEntity() {
    }

    public MonthlyExpenditureAggEntity(MonthlyExpenditureAggId id, BigDecimal totalCostUsd, boolean finalized, Instant finalizedAt) {
        this.id = id;
        this.totalCostUsd = totalCostUsd;
        this.finalized = finalized;
        this.finalizedAt = finalizedAt;
    }

    public MonthlyExpenditureAggId getId() {
        return id;
    }

    public BigDecimal getTotalCostUsd() {
        return totalCostUsd;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }
}
