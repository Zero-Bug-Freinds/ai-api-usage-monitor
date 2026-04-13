package com.eevee.usageservice.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UsageCostFinalizedService {

    private static final Logger log = LoggerFactory.getLogger(UsageCostFinalizedService.class);

    /**
     * Billing publishes {@link UsageCostFinalizedEvent} after commit; usage-service may still be
     * inserting the same {@code eventId} row from {@code usage.recorded}. Without retries, the
     * cost update affects 0 rows and {@code estimated_cost} stays at the proxy placeholder (often 0).
     */
    private static final int APPLY_COST_MAX_ATTEMPTS = 25;

    private static final long APPLY_COST_RETRY_INTERVAL_MS = 100L;

    private final UsageRecordedCostTransactionalWrites costWrites;

    public UsageCostFinalizedService(UsageRecordedCostTransactionalWrites costWrites) {
        this.costWrites = costWrites;
    }

    /**
     * Each update runs in {@link UsageRecordedCostTransactionalWrites} (REQUIRES_NEW) so a new
     * transaction sees rows committed by the parallel {@code usage.recorded} consumer.
     */
    public void applyCost(UsageCostFinalizedEvent event) {
        var eventId = event.eventId();
        var cost = event.estimatedCostUsd();
        int updated = 0;
        for (int attempt = 1; attempt <= APPLY_COST_MAX_ATTEMPTS; attempt++) {
            updated = costWrites.updateEstimatedCostByEventId(eventId, cost);
            if (updated > 0) {
                if (attempt > 1) {
                    log.info("Applied UsageCostFinalizedEvent after {} attempts eventId={}", attempt, eventId);
                }
                return;
            }
            if (attempt < APPLY_COST_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(APPLY_COST_RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting to retry cost apply eventId={}", eventId);
                    return;
                }
            }
        }
        log.warn(
                "usage_recorded_log row not found for cost finalization after {} attempts eventId={} cost={}",
                APPLY_COST_MAX_ATTEMPTS,
                eventId,
                cost);
    }
}
