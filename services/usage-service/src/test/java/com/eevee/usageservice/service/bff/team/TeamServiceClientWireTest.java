package com.eevee.usageservice.service.bff.team;

import com.eevee.usageservice.config.UsageServiceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies {@link TeamServiceClient} parses real team-service-shaped JSON over HTTP (task51-12 wire proof).
 */
class TeamServiceClientWireTest {

    private static final Executor DIRECT = Runnable::run;

    private MockRestServiceServer mockServer;
    private RestClient restClient;
    private TeamServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://team-service");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();

        UsageServiceProperties props = new UsageServiceProperties();
        props.getTeam().setBaseUrl("http://team-service");
        props.getTeam().setTeamListCacheTtlSeconds(60);
        props.getTeam().setMemberCacheTtlSeconds(60);
        props.getTeam().setCacheEmptyTeamList(false);

        client = new TeamServiceClient(props, DIRECT, restClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    @Test
    void fetchUserTeams_mapsStandardApiResponseDataArray() {
        String body = """
                {"success":true,"message":"사용자 팀 목록 조회에 성공했습니다","data":[
                  {"id":"7","name":"Engineering","createdAt":"2025-06-01T12:00:00Z"}
                ]}""";
        mockServer.expect(requestTo("http://team-service/internal/v1/users/user-42/teams"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<TeamSummaryClientItem> teams = client.fetchUserTeams("user-42");

        assertThat(teams).singleElement()
                .satisfies(t -> {
                    assertThat(t.id()).isEqualTo("7");
                    assertThat(t.name()).isEqualTo("Engineering");
                });
        mockServer.verify();
    }

    @Test
    void fetchUserTeams_mapsNestedDataTeamsAndSnakeCase() {
        String body = """
                {"success":true,"message":"ok","data":{"teams":[
                  {"team_id":3,"team_name":"Ops","created_at":"2025-01-01T00:00:00Z"}
                ]}}""";
        mockServer.expect(requestTo("http://team-service/internal/v1/users/u99/teams"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<TeamSummaryClientItem> teams = client.fetchUserTeams("u99");

        assertThat(teams).singleElement()
                .satisfies(t -> {
                    assertThat(t.id()).isEqualTo("3");
                    assertThat(t.name()).isEqualTo("Ops");
                });
        mockServer.verify();
    }
}
