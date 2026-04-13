package com.eevee.usageservice.service;

import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Isolated short transactions so cost updates see rows committed by the parallel
 * {@code usage.recorded} consumer (same {@code eventId}).
 */
@Component
class UsageRecordedCostTransactionalWrites {

    private final UsageRecordedLogRepository repository;

    UsageRecordedCostTransactionalWrites(UsageRecordedLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int updateEstimatedCostByEventId(UUID eventId, BigDecimal cost) {
        return repository.updateEstimatedCostByEventId(eventId, cost);
    }
}
