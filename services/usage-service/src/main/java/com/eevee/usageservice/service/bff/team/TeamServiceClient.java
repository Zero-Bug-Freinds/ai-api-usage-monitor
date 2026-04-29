package com.eevee.usageservice.service.bff.team;

import com.eevee.usageservice.api.dto.bff.TeamMemberProfile;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class TeamServiceClient {
    private final RestClient restClient;
    private final Executor usageBffExecutor;
    private final Cache<String, TeamEnrichmentResult> memberCache;
    private final int timeoutMs;

    public TeamServiceClient(
            UsageServiceProperties properties,
            @Qualifier("usageBffExecutor") Executor usageBffExecutor
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getTeam().getBaseUrl())
                .build();
        this.usageBffExecutor = usageBffExecutor;
        this.memberCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(60, properties.getTeam().getCacheTtlSeconds())))
                .maximumSize(1_000)
                .build();
        this.timeoutMs = properties.getTeam().getTimeoutMs();
    }

    public CompletableFuture<TeamEnrichmentResult> loadTeamEnrichment(String requesterUserId, String teamId) {
        String key = requesterUserId + ":" + teamId;
        TeamEnrichmentResult cached = memberCache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            List<String> warnings = new ArrayList<>();
            String teamName = null;
            List<TeamMemberProfile> profiles = List.of();

            try {
                teamName = fetchTeamName(teamId);
            } catch (Exception ex) {
                warnings.add("TEAM_NAME_UNAVAILABLE");
            }
            try {
                profiles = fetchMemberProfiles(requesterUserId, teamId);
            } catch (Exception ex) {
                warnings.add("TEAM_MEMBERS_UNAVAILABLE");
            }
            TeamEnrichmentResult result = new TeamEnrichmentResult(teamName, profiles, warnings);
            memberCache.put(key, result);
            return result;
        }, usageBffExecutor).orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private String fetchTeamName(String teamId) {
        JsonNode root = restClient.get()
                .uri("/internal/teams/{id}", teamId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        JsonNode data = root != null ? root.path("data") : null;
        if (data == null || data.isMissingNode()) {
            return null;
        }
        return nullIfBlank(data.path("teamName").asText(null));
    }

    private List<TeamMemberProfile> fetchMemberProfiles(String requesterUserId, String teamId) {
        JsonNode root = restClient.get()
                .uri("/api/v1/teams/{id}/members", teamId)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-User-Id", requesterUserId)
                .header("X-Team-Id", teamId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        JsonNode data = root != null ? root.path("data") : null;
        if (data == null || !data.isArray()) {
            return List.of();
        }
        List<TeamMemberProfile> out = new ArrayList<>();
        for (JsonNode n : data) {
            String userId = nullIfBlank(n.asText(null));
            if (userId != null) {
                out.add(new TeamMemberProfile(userId, userId, "UNKNOWN"));
            }
        }
        return out;
    }

    private static String nullIfBlank(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v;
    }
}
