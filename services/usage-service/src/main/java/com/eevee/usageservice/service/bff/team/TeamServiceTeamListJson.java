package com.eevee.usageservice.service.bff.team;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses team-service {@code ApiResponse} payloads for {@code GET /internal/v1/users/{userId}/teams}.
 * Supports {@code data} as a JSON array (current contract) or {@code data.teams}, and common
 * camelCase / snake_case field aliases so rows are not dropped on naming drift.
 */
final class TeamServiceTeamListJson {

    private TeamServiceTeamListJson() {
    }

    /**
     * Resolves the JSON array that holds team summary objects, or {@code null} if the shape is unexpected.
     */
    static JsonNode resolveTeamListArray(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        JsonNode data = root.get("data");
        if (data != null && data.isArray()) {
            return data;
        }
        if (data != null && data.isObject()) {
            JsonNode nested = data.get("teams");
            if (nested != null && nested.isArray()) {
                return nested;
            }
        }
        return null;
    }

    static ParsedTeamList parseTeamItems(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return new ParsedTeamList(List.of(), 0, 0);
        }
        List<TeamSummaryClientItem> teams = new ArrayList<>();
        int skipped = 0;
        for (JsonNode n : arrayNode) {
            String id = extractTeamListItemId(n);
            String name = extractTeamListItemName(n);
            if (id == null || name == null) {
                skipped++;
                continue;
            }
            teams.add(new TeamSummaryClientItem(id, name, nullIfBlank(extractCreatedAtRaw(n))));
        }
        return new ParsedTeamList(teams, arrayNode.size(), skipped);
    }

    record ParsedTeamList(List<TeamSummaryClientItem> items, int sourceRowCount, int skippedRows) {
    }

    private static String extractCreatedAtRaw(JsonNode n) {
        String v = nullIfBlank(n.path("createdAt").asText(null));
        if (v != null) {
            return v;
        }
        return nullIfBlank(n.path("created_at").asText(null));
    }

    /** Accepts string or numeric {@code id} / {@code teamId} / {@code team_id} from team-service JSON. */
    static String extractTeamListItemId(JsonNode n) {
        String v = textOrNumericId(n, "id");
        if (v != null) {
            return v;
        }
        v = textOrNumericId(n, "teamId");
        if (v != null) {
            return v;
        }
        return textOrNumericId(n, "team_id");
    }

    static String extractTeamListItemName(JsonNode n) {
        String v = nullIfBlank(n.path("name").asText(null));
        if (v != null) {
            return v;
        }
        v = nullIfBlank(n.path("teamName").asText(null));
        if (v != null) {
            return v;
        }
        return nullIfBlank(n.path("team_name").asText(null));
    }

    private static String textOrNumericId(JsonNode n, String field) {
        JsonNode idNode = n.path(field);
        if (idNode.isMissingNode() || idNode.isNull()) {
            return null;
        }
        if (idNode.isNumber()) {
            return Long.toString(idNode.longValue());
        }
        return nullIfBlank(idNode.asText(null));
    }

    private static String nullIfBlank(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v;
    }
}
