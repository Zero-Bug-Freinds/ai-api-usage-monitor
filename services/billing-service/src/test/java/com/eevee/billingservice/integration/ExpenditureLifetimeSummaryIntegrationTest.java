package com.eevee.billingservice.integration;

import com.eevee.billingservice.domain.DailyExpenditureAggEntity;
import com.eevee.billingservice.domain.DailyExpenditureAggId;
import com.eevee.billingservice.repository.DailyExpenditureAggRepository;
import com.eevee.usage.events.AiProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "billing.analytics.lifetime-range-days=7"
})
@Tag("integration")
@AutoConfigureMockMvc
class ExpenditureLifetimeSummaryIntegrationTest extends AbstractBillingIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DailyExpenditureAggRepository dailyRepository;

    @Test
    void lifetimeSummary_requiresGatewayHeaders() throws Exception {
        mvc.perform(get("/api/v1/expenditure/summary/lifetime")
                        .param("apiKeyId", "k1")
                        .param("provider", "OPENAI"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lifetimeSummary_returnsTotalForWindow() throws Exception {
        String userId = "user-it";
        String apiKeyId = "k1";
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDate inWindow = today.minusDays(2);
        LocalDate outOfWindow = today.minusDays(30);

        dailyRepository.save(new DailyExpenditureAggEntity(
                new DailyExpenditureAggId(outOfWindow, userId, apiKeyId, AiProvider.OPENAI, "gpt-4o-mini"),
                new BigDecimal("99.0"),
                0L,
                0L
        ));
        dailyRepository.save(new DailyExpenditureAggEntity(
                new DailyExpenditureAggId(inWindow, userId, apiKeyId, AiProvider.OPENAI, "gpt-4o-mini"),
                new BigDecimal("1.25"),
                0L,
                0L
        ));

        mvc.perform(get("/api/v1/expenditure/summary/lifetime")
                        .header("X-User-Id", userId)
                        .header("X-Gateway-Auth", "test-secret")
                        .param("apiKeyId", apiKeyId)
                        .param("provider", "OPENAI")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCostUsd").value(1.25));
    }
}

