package com.eevee.billingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "billing_user_api_key_seen")
public class BillingUserApiKeySeenEntity {

    @EmbeddedId
    private BillingUserApiKeySeenId id;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    protected BillingUserApiKeySeenEntity() {
    }

    public BillingUserApiKeySeenEntity(BillingUserApiKeySeenId id, Instant firstSeenAt) {
        this.id = id;
        this.firstSeenAt = firstSeenAt;
    }

    public BillingUserApiKeySeenId getId() {
        return id;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }
}
