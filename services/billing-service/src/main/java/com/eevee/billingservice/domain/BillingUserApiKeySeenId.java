package com.eevee.billingservice.domain;

import com.eevee.usage.events.AiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class BillingUserApiKeySeenId implements Serializable {

    @Column(name = "user_id", nullable = false, length = 256)
    private String userId;

    @Column(name = "api_key_id", nullable = false, length = 256)
    private String apiKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AiProvider provider;

    protected BillingUserApiKeySeenId() {
    }

    public BillingUserApiKeySeenId(String userId, String apiKeyId, AiProvider provider) {
        this.userId = userId;
        this.apiKeyId = apiKeyId;
        this.provider = provider;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BillingUserApiKeySeenId that = (BillingUserApiKeySeenId) o;
        return Objects.equals(userId, that.userId)
                && Objects.equals(apiKeyId, that.apiKeyId)
                && provider == that.provider;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, apiKeyId, provider);
    }
}
