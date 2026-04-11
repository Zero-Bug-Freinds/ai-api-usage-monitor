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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageCostFinalizedServiceTest {

    @Mock
    private UsageRecordedLogRepository repository;

    @InjectMocks
    private UsageCostFinalizedService service;

    @Test
    void applyCost_updatesRepository() {
        UUID id = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("1.00");
        UsageCostFinalizedEvent ev = UsageCostFinalizedEvent.v1(id, cost);
        when(repository.updateEstimatedCostByEventId(eq(id), argThat(c -> c.compareTo(cost) == 0))).thenReturn(1);

        service.applyCost(ev);

        verify(repository).updateEstimatedCostByEventId(eq(id), argThat(c -> c.compareTo(cost) == 0));
    }
}
