package com.eevee.billingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_processed_event")
public class BillingProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /**
     * When true, a {@link com.eevee.usage.events.UsageCostFinalizedEvent} should be emitted for this row
     * (successful billable path). When false, processing is complete without a cost event (skipped or feature off).
     */
    @Column(name = "cost_event_applicable", nullable = false, columnDefinition = "boolean default false")
    private boolean costEventApplicable;

    /**
     * Set when the cost-finalized message was published successfully to RabbitMQ.
     */
    @Column(name = "cost_event_published_at")
    private Instant costEventPublishedAt;

    protected BillingProcessedEventEntity() {
    }

    public BillingProcessedEventEntity(UUID eventId, Instant processedAt, boolean costEventApplicable) {
        this.eventId = eventId;
        this.processedAt = processedAt;
        this.costEventApplicable = costEventApplicable;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public boolean isCostEventApplicable() {
        return costEventApplicable;
    }

    public Instant getCostEventPublishedAt() {
        return costEventPublishedAt;
    }

    public void setCostEventPublishedAt(Instant costEventPublishedAt) {
        this.costEventPublishedAt = costEventPublishedAt;
    }
}
