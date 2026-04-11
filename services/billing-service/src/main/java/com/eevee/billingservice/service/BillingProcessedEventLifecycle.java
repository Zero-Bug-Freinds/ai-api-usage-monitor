package com.eevee.billingservice.service;

import com.eevee.billingservice.domain.BillingProcessedEventEntity;
import com.eevee.billingservice.repository.BillingProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Updates processed-event rows after the main transaction commits (e.g. cost publish watermark).
 */
@Service
public class BillingProcessedEventLifecycle {

    private final BillingProcessedEventRepository processedEventRepository;

    public BillingProcessedEventLifecycle(BillingProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCostEventPublished(UUID eventId, Instant publishedAt) {
        BillingProcessedEventEntity row = processedEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("missing billing_processed_event row: " + eventId));
        row.setCostEventPublishedAt(publishedAt);
        processedEventRepository.save(row);
    }
}
