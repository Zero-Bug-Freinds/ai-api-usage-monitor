package com.eevee.usageservice.service;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageCostFinalizedServiceTest {

    @Mock
    private UsageRecordedCostTransactionalWrites costWrites;

    @InjectMocks
    private UsageCostFinalizedService service;

    @Test
    void applyCost_updatesRepository() {
        UUID id = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("1.00");
        UsageCostFinalizedEvent ev = UsageCostFinalizedEvent.v1(id, cost);
        when(costWrites.updateEstimatedCostByEventId(eq(id), argThat(c -> c.compareTo(cost) == 0))).thenReturn(1);

        service.applyCost(ev);

        verify(costWrites).updateEstimatedCostByEventId(eq(id), argThat(c -> c.compareTo(cost) == 0));
    }

    @Test
    void applyCost_retriesUntilRowVisible() {
        UUID id = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("0.01");
        UsageCostFinalizedEvent ev = UsageCostFinalizedEvent.v1(id, cost);
        when(costWrites.updateEstimatedCostByEventId(eq(id), argThat(c -> c.compareTo(cost) == 0)))
                .thenReturn(0, 0, 1);

        service.applyCost(ev);

        verify(costWrites, times(3)).updateEstimatedCostByEventId(eq(id), argThat(c -> c.compareTo(cost) == 0));
    }
}
