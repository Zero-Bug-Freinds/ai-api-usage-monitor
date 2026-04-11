package com.eevee.billingservice.api;

import com.eevee.billingservice.config.BillingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingRestExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/__test__/db-fail")
        void fail() {
            throw new DataIntegrityViolationException("simulated");
        }
    }

    @Test
    void dataAccessExceptionReturns503WithBody() throws Exception {
        BillingProperties props = new BillingProperties();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new BillingRestExceptionHandler(props))
                .build();

        mvc.perform(get("/__test__/db-fail").header("X-Correlation-Id", "cid-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.hint").exists());
    }

    @Test
    void hintOmittedWhenDisabled() throws Exception {
        BillingProperties props = new BillingProperties();
        props.getError().setExposeDatasourceFailureHint(false);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new BillingRestExceptionHandler(props))
                .build();

        mvc.perform(get("/__test__/db-fail"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.hint").doesNotExist());
    }
}
