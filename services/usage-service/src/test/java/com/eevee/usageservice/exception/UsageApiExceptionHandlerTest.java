package com.eevee.usageservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsageApiExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/__test__/bad-arg")
        void badArg() {
            throw new IllegalArgumentException("from and to are required");
        }

        @GetMapping("/__test__/bad-state")
        void badState() {
            throw new IllegalStateException("team-service circuit is open");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new UsageApiExceptionHandler())
            .build();

    @Test
    void illegalArgumentReturns400WithMaskedMessage() throws Exception {
        mvc.perform(get("/__test__/bad-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value(UsageApiExceptionHandler.CLIENT_MESSAGE_BAD_REQUEST));
    }

    @Test
    void illegalStateReturns500WithMaskedMessage() throws Exception {
        mvc.perform(get("/__test__/bad-state"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("internal_error"))
                .andExpect(jsonPath("$.message").value(UsageApiExceptionHandler.CLIENT_MESSAGE_INTERNAL_ERROR));
    }
}
