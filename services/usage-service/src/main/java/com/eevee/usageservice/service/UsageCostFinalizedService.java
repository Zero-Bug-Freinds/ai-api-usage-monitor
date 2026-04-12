package com.eevee.usageservice.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageCostFinalizedService {

    private static final Logger log = LoggerFactory.getLogger(UsageCostFinalizedService.class);

    private final UsageRecordedLogRepository repository;

    public UsageCostFinalizedService(UsageRecordedLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void applyCost(UsageCostFinalizedEvent event) {
        int updated = repository.updateEstimatedCostByEventId(event.eventId(), event.estimatedCostUsd());
        if (updated == 0) {
            log.debug(
                    "usage_recorded_log row not found for cost finalization eventId={} (row may arrive later)",
                    event.eventId());
        }
    }
}
