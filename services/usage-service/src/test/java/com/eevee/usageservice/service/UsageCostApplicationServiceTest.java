package com.eevee.usageservice.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageCostApplicationServiceTest {

    @Mock
    private UsageRecordedLogRepository repository;

    @InjectMocks
    private UsageCostApplicationService usageCostApplicationService;

    @Test
    void skipsWhenEventIdNull() {
        usageCostApplicationService.applyCost(new UsageCostFinalizedEvent(null, BigDecimal.ONE, null, null));
        verifyNoInteractions(repository);
    }

    @Test
    void skipsWhenEstimatedCostNull() {
        UUID id = UUID.randomUUID();
        usageCostApplicationService.applyCost(new UsageCostFinalizedEvent(id, null, null, null));
        verifyNoInteractions(repository);
    }

    @Test
    void updatesWhenValid() {
        UUID id = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("0.0500");
        when(repository.updateEstimatedCostByEventId(id, cost)).thenReturn(1);

        usageCostApplicationService.applyCost(new UsageCostFinalizedEvent(id, cost, "USD", null));

        verify(repository).updateEstimatedCostByEventId(eq(id), eq(cost));
    }
}
