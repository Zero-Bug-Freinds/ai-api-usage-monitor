package com.zerobugfreinds.identity_service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity_service.security.JwtTokenProvider;
import com.zerobugfreinds.identity_service.service.TeamMembershipVerificationClient;
import io.jsonwebtoken.Claims;
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
    private JwtTokenProvider jwtTokenProvider;

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
        Claims firstClaims = jwtTokenProvider.validateAndGetClaims(firstAccessToken);
        assertThat(firstClaims.get("active_team_id")).isNull();

        Long targetTeamId = 701L;
        Long userId = claimAsLong(firstClaims.get("userId"));
        when(teamMembershipVerificationClient.isActiveTeamMember(eq(targetTeamId), eq(userId))).thenReturn(true);

        MvcResult switchResult = mockMvc.perform(post("/api/auth/token/switch-team")
                        .header("Authorization", "Bearer " + firstAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetTeamId": 701
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String switchedAccessToken = accessTokenFrom(switchResult);
        Claims switchedClaims = jwtTokenProvider.validateAndGetClaims(switchedAccessToken);
        assertThat(claimAsLong(switchedClaims.get("active_team_id"))).isEqualTo(targetTeamId);
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

        mockMvc.perform(post("/api/auth/external-keys")
                        .header("Authorization", "Bearer " + accessToken)
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
                        .header("Authorization", "Bearer " + accessToken))
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

    private static Long claimAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        TeamMembershipVerificationClient teamMembershipVerificationClient() {
            return mock(TeamMembershipVerificationClient.class);
        }
    }
}
