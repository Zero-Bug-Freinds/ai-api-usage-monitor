package com.eevee.billingservice.domain;

import com.eevee.usage.events.AiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class DailyExpenditureAggId implements Serializable {

    @Column(name = "agg_date", nullable = false)
    private LocalDate aggDate;

    @Column(name = "user_id", nullable = false, length = 256)
    private String userId;

    @Column(name = "api_key_id", nullable = false, length = 256)
    private String apiKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AiProvider provider;

    @Column(name = "model", nullable = false, length = 512)
    private String model;

    protected DailyExpenditureAggId() {
    }

    public DailyExpenditureAggId(
            LocalDate aggDate,
            String userId,
            String apiKeyId,
            AiProvider provider,
            String model
    ) {
        this.aggDate = aggDate;
        this.userId = userId;
        this.apiKeyId = apiKeyId;
        this.provider = provider;
        this.model = model;
    }

    public LocalDate getAggDate() {
        return aggDate;
    }

    public String getUserId() {
        return userId;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DailyExpenditureAggId that = (DailyExpenditureAggId) o;
        return Objects.equals(aggDate, that.aggDate)
                && Objects.equals(userId, that.userId)
                && Objects.equals(apiKeyId, that.apiKeyId)
                && provider == that.provider
                && Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggDate, userId, apiKeyId, provider, model);
    }
}
