package com.zerobugfreinds.identity_service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity_service.security.GatewayHeaderInterceptor;
import com.zerobugfreinds.identity_service.service.TeamMembershipVerificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
class AuthSwitchTeamE2ETest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TeamMembershipVerificationClient teamMembershipVerificationClient;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void loginThenSwitchTeam_issuesTokenWithActiveTeamIdClaim() throws Exception {
        signup("switch-user@local.test", "switched-user");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "switch-user@local.test",
                                  "password": "test1234!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String firstAccessToken = accessTokenFrom(loginResult);
        JsonNode firstClaims = payloadClaims(firstAccessToken);
        assertThat(firstClaims.path("active_team_id").isMissingNode()).isTrue();

        Long targetTeamId = 701L;
        Long userId = claimAsLong(firstClaims.path("userId"));
        when(teamMembershipVerificationClient.isActiveTeamMember(eq(targetTeamId), eq(userId))).thenReturn(true);

        MvcResult switchResult = mockMvc.perform(post("/api/auth/token/switch-team")
                        .header(GatewayHeaderInterceptor.USER_ID_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetTeamId": 701
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String switchedAccessToken = accessTokenFrom(switchResult);
        JsonNode switchedClaims = payloadClaims(switchedAccessToken);
        assertThat(claimAsLong(switchedClaims.path("active_team_id"))).isEqualTo(targetTeamId);
    }

    @Test
    void personalExternalApiKeyFlow_stillWorksWithUpdatedJwtStructure() throws Exception {
        signup("apikey-user@local.test", "apikey-user");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "apikey-user@local.test",
                                  "password": "test1234!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = accessTokenFrom(loginResult);
        JsonNode accessClaims = payloadClaims(accessToken);
        Long userId = claimAsLong(accessClaims.path("userId"));

        mockMvc.perform(post("/api/auth/external-keys")
                        .header(GatewayHeaderInterceptor.USER_ID_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "OPENAI",
                                  "externalKey": "sk-test-regression-key",
                                  "alias": "personal-main",
                                  "monthlyBudgetUsd": 10.50
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/auth/external-keys")
                        .header(GatewayHeaderInterceptor.USER_ID_HEADER, userId))
                .andExpect(status().isOk());
    }

    private void signup(String email, String name) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "test1234!",
                                  "passwordConfirm": "test1234!",
                                  "name": "%s",
                                  "role": "USER"
                                }
                                """.formatted(email, name)))
                .andExpect(status().isCreated());
    }

    private String accessTokenFrom(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private JsonNode payloadClaims(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("JWT payload를 읽을 수 없습니다");
        }
        byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readTree(payloadBytes);
    }

    private static Long claimAsLong(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        return Long.parseLong(value.asText());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        TeamMembershipVerificationClient teamMembershipVerificationClient() {
            return mock(TeamMembershipVerificationClient.class);
        }
    }
}
