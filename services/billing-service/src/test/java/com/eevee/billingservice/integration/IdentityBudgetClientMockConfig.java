package com.eevee.billingservice.integration;

import com.eevee.usage.events.AiProvider;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Test-only stub so {@link com.eevee.billingservice.service.ExpenditureQueryService} can run without Identity HTTP.
 */
@TestConfiguration
public class IdentityBudgetClientMockConfig {

    @Bean
    @Primary
    IdentityBudgetClient identityBudgetClient() {
        IdentityBudgetClient mock = Mockito.mock(IdentityBudgetClient.class);
        lenient().when(mock.fetchMonthlyBudgetUsdForKey(anyString(), any(AiProvider.class), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(mock.fetchMonthlyBudgetUsd(anyString())).thenReturn(Optional.empty());
        lenient().when(mock.fetchMonthlyBudgetEnvelope(anyString())).thenReturn(Optional.empty());
        return mock;
    }
}
