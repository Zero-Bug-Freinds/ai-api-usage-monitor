package com.eevee.billingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "billing_team_api_key")
public class BillingTeamApiKeyEntity {

    @Id
    @Column(name = "team_api_key_id", nullable = false)
    private Long teamApiKeyId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "alias", nullable = false)
    private String alias;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "monthly_budget_usd", nullable = false, precision = 19, scale = 6)
    private BigDecimal monthlyBudgetUsd;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "retain_logs")
    private Boolean retainLogs;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BillingTeamApiKeyEntity() {
    }

    public BillingTeamApiKeyEntity(
            Long teamApiKeyId,
            Long teamId,
            String alias,
            String provider,
            BigDecimal monthlyBudgetUsd,
            String status,
            Boolean retainLogs,
            Instant occurredAt,
            Instant updatedAt
    ) {
        this.teamApiKeyId = teamApiKeyId;
        this.teamId = teamId;
        this.alias = alias;
        this.provider = provider;
        this.monthlyBudgetUsd = monthlyBudgetUsd;
        this.status = status;
        this.retainLogs = retainLogs;
        this.occurredAt = occurredAt;
        this.updatedAt = updatedAt;
    }

    public Long getTeamApiKeyId() {
        return teamApiKeyId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public String getAlias() {
        return alias;
    }

    public String getProvider() {
        return provider;
    }

    public BigDecimal getMonthlyBudgetUsd() {
        return monthlyBudgetUsd;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getRetainLogs() {
        return retainLogs;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void applyStatusChanged(
            Long teamId,
            String alias,
            String provider,
            BigDecimal monthlyBudgetUsd,
            String status,
            Boolean retainLogs,
            Instant occurredAt,
            Instant updatedAt
    ) {
        this.teamId = teamId;
        this.alias = alias;
        this.provider = provider;
        this.monthlyBudgetUsd = monthlyBudgetUsd;
        this.status = status;
        this.retainLogs = retainLogs;
        this.occurredAt = occurredAt;
        this.updatedAt = updatedAt;
    }
}

