package com.eevee.usageservice.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageCostApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UsageCostApplicationService.class);

    private final UsageRecordedLogRepository repository;

    public UsageCostApplicationService(UsageRecordedLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Applies billing-computed cost to the usage log row keyed by {@link UsageCostFinalizedEvent#eventId()}.
     * Idempotent for the same eventId and amount. Missing rows are logged (ordering vs usage insert).
     */
    @Transactional
    public void applyCost(UsageCostFinalizedEvent event) {
        if (event == null) {
            log.warn("Skipping null UsageCostFinalizedEvent");
            return;
        }
        if (event.eventId() == null) {
            log.warn("Skipping UsageCostFinalizedEvent with null eventId");
            return;
        }
        if (event.estimatedCost() == null) {
            log.warn("Skipping UsageCostFinalizedEvent with null estimatedCost eventId={}", event.eventId());
            return;
        }
        int updated = repository.updateEstimatedCostByEventId(event.eventId(), event.estimatedCost());
        if (updated == 0) {
            log.warn(
                    "No usage_recorded_log row for eventId={} (cost event may have arrived before usage insert)",
                    event.eventId()
            );
        } else {
            log.debug("Applied estimated cost for eventId={}", event.eventId());
        }
    }
}
