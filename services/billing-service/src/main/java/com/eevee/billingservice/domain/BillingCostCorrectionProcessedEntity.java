package com.eevee.billingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_cost_correction_processed")
public class BillingCostCorrectionProcessedEntity {

    @Id
    @Column(name = "correction_event_id", nullable = false)
    private UUID correctionEventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected BillingCostCorrectionProcessedEntity() {
    }

    public BillingCostCorrectionProcessedEntity(UUID correctionEventId, Instant processedAt) {
        this.correctionEventId = correctionEventId;
        this.processedAt = processedAt;
    }

    public UUID getCorrectionEventId() {
        return correctionEventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
