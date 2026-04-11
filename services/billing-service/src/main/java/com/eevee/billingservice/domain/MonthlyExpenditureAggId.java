package com.eevee.billingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MonthlyExpenditureAggId implements Serializable {

    /**
     * First calendar day of the month in Asia/Seoul (YYYY-MM-01).
     */
    @Column(name = "month_start_date", nullable = false)
    private java.time.LocalDate monthStartDate;

    @Column(name = "user_id", nullable = false, length = 256)
    private String userId;

    @Column(name = "api_key_id", nullable = false, length = 256)
    private String apiKeyId;

    protected MonthlyExpenditureAggId() {
    }

    public MonthlyExpenditureAggId(java.time.LocalDate monthStartDate, String userId, String apiKeyId) {
        this.monthStartDate = monthStartDate;
        this.userId = userId;
        this.apiKeyId = apiKeyId;
    }

    public java.time.LocalDate getMonthStartDate() {
        return monthStartDate;
    }

    public String getUserId() {
        return userId;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MonthlyExpenditureAggId that = (MonthlyExpenditureAggId) o;
        return Objects.equals(monthStartDate, that.monthStartDate)
                && Objects.equals(userId, that.userId)
                && Objects.equals(apiKeyId, that.apiKeyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(monthStartDate, userId, apiKeyId);
    }
}
