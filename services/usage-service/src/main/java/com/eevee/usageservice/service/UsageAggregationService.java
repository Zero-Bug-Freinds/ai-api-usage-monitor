package com.eevee.usageservice.service;

import com.eevee.usageservice.mq.UsageSummaryAggregationMessage;
import com.eevee.usageservice.repository.analytics.DailyUsageSummaryAggregationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageAggregationService {

    private static final Logger log = LoggerFactory.getLogger(UsageAggregationService.class);

    private final DailyUsageSummaryAggregationRepository aggregationRepository;

    public UsageAggregationService(DailyUsageSummaryAggregationRepository aggregationRepository) {
        this.aggregationRepository = aggregationRepository;
    }

    @Transactional
    public void applyFromEvent(UsageSummaryAggregationMessage message) {
        long startedAt = System.nanoTime();
        if (!aggregationRepository.registerProcessedEvent(message.eventId())) {
            log.debug("Skipping duplicate summary aggregation eventId={}", message.eventId());
            return;
        }
        aggregationRepository.upsertDailySummary(message);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.debug("Applied summary aggregation eventId={} elapsedMs={}", message.eventId(), elapsedMs);
    }

    @Transactional
    public void applyFromBackfill(UsageSummaryAggregationMessage message) {
        aggregationRepository.upsertDailySummary(message);
    }
}
