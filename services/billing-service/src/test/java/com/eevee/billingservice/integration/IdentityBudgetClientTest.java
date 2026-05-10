package com.eevee.billingservice.integration;

import com.eevee.billingservice.config.IdentityProperties;
import com.eevee.usage.events.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class IdentityBudgetClientTest {

    private IdentityBudgetClient newSpyClient() {
        IdentityProperties props = mock(IdentityProperties.class);
        return spy(new IdentityBudgetClient(props, new ObjectMapper()));
    }

    @Test
    void fetchMonthlyBudgetKeyRow_matchesGoogleRowWhenBillingProviderIsGoogle() {
        IdentityBudgetClient client = newSpyClient();
        IdentityMonthlyBudgetEnvelope env = new IdentityMonthlyBudgetEnvelope(
                BigDecimal.TEN,
                List.of(new IdentityBudgetKeyRow(99L, "GOOGLE", "alias", new BigDecimal("12.34")))
        );
        doReturn(Optional.of(env)).when(client).fetchMonthlyBudgetEnvelope(eq("user-a"));

        Optional<IdentityBudgetKeyRow> row = client.fetchMonthlyBudgetKeyRow("user-a", AiProvider.GOOGLE, "99");

        assertThat(row).isPresent();
        assertThat(row.get().monthlyBudgetUsd()).isEqualByComparingTo("12.34");
    }

    @Test
    void fetchMonthlyBudgetKeyRow_matchesLegacyGeminiRowWhenBillingProviderIsGoogle() {
        IdentityBudgetClient client = newSpyClient();
        IdentityMonthlyBudgetEnvelope env = new IdentityMonthlyBudgetEnvelope(
                BigDecimal.ZERO,
                List.of(new IdentityBudgetKeyRow(1L, "GEMINI", "legacy", new BigDecimal("7.00")))
        );
        doReturn(Optional.of(env)).when(client).fetchMonthlyBudgetEnvelope(eq("u"));

        Optional<IdentityBudgetKeyRow> row = client.fetchMonthlyBudgetKeyRow("u", AiProvider.GOOGLE, "1");

        assertThat(row).isPresent();
        assertThat(row.get().monthlyBudgetUsd()).isEqualByComparingTo("7.00");
    }

    @Test
    void fetchMonthlyBudgetKeyRow_openaiRowStillMatchesOpenai() {
        IdentityBudgetClient client = newSpyClient();
        IdentityMonthlyBudgetEnvelope env = new IdentityMonthlyBudgetEnvelope(
                null,
                List.of(new IdentityBudgetKeyRow(5L, "OPENAI", "o", new BigDecimal("3.00")))
        );
        doReturn(Optional.of(env)).when(client).fetchMonthlyBudgetEnvelope(eq("x"));

        assertThat(client.fetchMonthlyBudgetKeyRow("x", AiProvider.OPENAI, "5")).isPresent();
    }

    @Test
    void fetchMonthlyBudgetKeyRow_googleRequestDoesNotMatchOpenaiRow() {
        IdentityBudgetClient client = newSpyClient();
        IdentityMonthlyBudgetEnvelope env = new IdentityMonthlyBudgetEnvelope(
                null,
                List.of(new IdentityBudgetKeyRow(5L, "OPENAI", "o", new BigDecimal("3.00")))
        );
        doReturn(Optional.of(env)).when(client).fetchMonthlyBudgetEnvelope(eq("x"));

        assertThat(client.fetchMonthlyBudgetKeyRow("x", AiProvider.GOOGLE, "5")).isEmpty();
    }
}
