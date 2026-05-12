package com.eevee.usageservice.service.bff.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TeamServiceTeamListJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parseTeamItems_standardApiResponseDataArray() throws Exception {
        String json = """
                {"success":true,"message":"ok","data":[
                  {"id":"12","name":"Alpha","createdAt":"2024-01-02T00:00:00Z"}
                ]}""";
        JsonNode root = MAPPER.readTree(json);
        JsonNode arr = TeamServiceTeamListJson.resolveTeamListArray(root);
        TeamServiceTeamListJson.ParsedTeamList parsed = TeamServiceTeamListJson.parseTeamItems(arr);
        assertThat(parsed.items()).singleElement()
                .satisfies(t -> {
                    assertThat(t.id()).isEqualTo("12");
                    assertThat(t.name()).isEqualTo("Alpha");
                    assertThat(t.createdAt()).isEqualTo(Instant.parse("2024-01-02T00:00:00Z"));
                });
        assertThat(parsed.skippedRows()).isZero();
    }

    @Test
    void parseTeamItems_snakeCaseTeamFields() throws Exception {
        String json = """
                {"success":true,"message":"ok","data":[
                  {"team_id":99,"team_name":"Beta","created_at":"2024-03-01T00:00:00Z"}
                ]}""";
        JsonNode root = MAPPER.readTree(json);
        JsonNode arr = TeamServiceTeamListJson.resolveTeamListArray(root);
        TeamServiceTeamListJson.ParsedTeamList parsed = TeamServiceTeamListJson.parseTeamItems(arr);
        assertThat(parsed.items()).singleElement()
                .satisfies(t -> {
                    assertThat(t.id()).isEqualTo("99");
                    assertThat(t.name()).isEqualTo("Beta");
                    assertThat(t.createdAt()).isEqualTo(Instant.parse("2024-03-01T00:00:00Z"));
                });
    }

    @Test
    void resolveTeamListArray_nestedDataTeams() throws Exception {
        String json = """
                {"success":true,"data":{"teams":[{"id":"1","name":"T","createdAt":null}]}}""";
        JsonNode root = MAPPER.readTree(json);
        JsonNode arr = TeamServiceTeamListJson.resolveTeamListArray(root);
        assertThat(arr).isNotNull();
        assertThat(arr.isArray()).isTrue();
        TeamServiceTeamListJson.ParsedTeamList parsed = TeamServiceTeamListJson.parseTeamItems(arr);
        assertThat(parsed.items()).hasSize(1);
        assertThat(parsed.items().getFirst().id()).isEqualTo("1");
    }

    @Test
    void parseTeamItems_numericId() throws Exception {
        String json = """
                {"success":true,"data":[{"id":5,"name":"N","createdAt":null}]}""";
        JsonNode root = MAPPER.readTree(json);
        JsonNode arr = TeamServiceTeamListJson.resolveTeamListArray(root);
        TeamServiceTeamListJson.ParsedTeamList parsed = TeamServiceTeamListJson.parseTeamItems(arr);
        assertThat(parsed.items().getFirst().id()).isEqualTo("5");
    }
}
