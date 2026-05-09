package com.eevee.usageservice.service.bff.team;

import com.eevee.usageservice.api.dto.bff.TeamMemberProfile;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class TeamServiceClient {
    private static final Logger log = LoggerFactory.getLogger(TeamServiceClient.class);
    private final RestClient restClient;
    private final Executor usageBffExecutor;
    private final Cache<String, TeamEnrichmentResult> memberCache;
    private final Cache<String, List<TeamSummaryClientItem>> userTeamsCache;
    private final int timeoutMs;
    private final CircuitBreaker teamServiceCircuitBreaker;

    public TeamServiceClient(
            UsageServiceProperties properties,
            @Qualifier("usageBffExecutor") Executor usageBffExecutor
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getTeam().getBaseUrl())
                .build();
        this.usageBffExecutor = usageBffExecutor;
        this.userTeamsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(60, properties.getTeam().getTeamListCacheTtlSeconds())))
                .maximumSize(2_000)
                .build();
        this.memberCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(60, properties.getTeam().getMemberCacheTtlSeconds())))
                .maximumSize(1_000)
                .build();
        this.teamServiceCircuitBreaker = CircuitBreaker.ofDefaults("usage-team-service");
        this.timeoutMs = properties.getTeam().getTimeoutMs();
    }

    public List<TeamSummaryClientItem> fetchUserTeams(String requesterUserId) {
        return fetchUserTeams(requesterUserId, null);
    }

    public List<TeamSummaryClientItem> fetchUserTeams(String requesterUserId, String fallbackRequesterUserId) {
        if (!StringUtils.hasText(requesterUserId)) {
            throw new IllegalArgumentException("requester userId is required");
        }
        String primaryRequester = requesterUserId.trim();
        String fallbackRequester = normalizeFallback(fallbackRequesterUserId, primaryRequester);
        String cacheKey = fallbackRequester == null
                ? primaryRequester
                : primaryRequester + "|" + fallbackRequester;
        List<TeamSummaryClientItem> cached = userTeamsCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<TeamSummaryClientItem> result = List.of();
        try {
            result = callWithCircuitBreaker(() -> fetchUserTeamsInternal(primaryRequester));
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch teams from team-service requesterUserId={} reason={}", primaryRequester, ex.getMessage());
        }
        if (result.isEmpty() && fallbackRequester != null) {
            try {
                result = callWithCircuitBreaker(() -> fetchUserTeamsInternal(fallbackRequester));
            } catch (RuntimeException ex) {
                log.warn(
                        "Failed to fetch teams from team-service fallbackRequesterUserId={} reason={}",
                        fallbackRequester,
                        ex.getMessage()
                );
                result = List.of();
            }
        }
        userTeamsCache.put(cacheKey, result);
        return result;
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
        JsonNode root = callWithCircuitBreaker(() -> restClient.get()
                .uri("/internal/teams/{id}", teamId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        JsonNode data = root != null ? root.path("data") : null;
        if (data == null || data.isMissingNode()) {
            return null;
        }
        return nullIfBlank(data.path("teamName").asText(null));
    }

    private List<TeamMemberProfile> fetchMemberProfiles(String requesterUserId, String teamId) {
        JsonNode root = callWithCircuitBreaker(() -> restClient.get()
                .uri("/api/v1/teams/{id}/members", teamId)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-User-Id", requesterUserId)
                .header("X-Team-Id", teamId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
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

    private List<TeamSummaryClientItem> fetchUserTeamsInternal(String requesterUserId) {
        JsonNode root = restClient.get()
                .uri("/internal/v1/users/{userId}/teams", requesterUserId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        JsonNode data = root != null ? root.path("data") : null;
        if (data == null || !data.isArray()) {
            return List.of();
        }
        List<TeamSummaryClientItem> teams = new ArrayList<>();
        for (JsonNode n : data) {
            String id = nullIfBlank(n.path("id").asText(null));
            String name = nullIfBlank(n.path("name").asText(null));
            if (id == null || name == null) {
                continue;
            }
            teams.add(new TeamSummaryClientItem(id, name, nullIfBlank(n.path("createdAt").asText(null))));
        }
        return teams;
    }

    private <T> T callWithCircuitBreaker(java.util.function.Supplier<T> supplier) {
        try {
            return CircuitBreaker.decorateSupplier(teamServiceCircuitBreaker, supplier).get();
        } catch (CallNotPermittedException ex) {
            throw new IllegalStateException("team-service circuit is open", ex);
        }
    }

    private static String nullIfBlank(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v;
    }

    private static String normalizeFallback(String fallbackRequesterUserId, String primaryRequester) {
        if (!StringUtils.hasText(fallbackRequesterUserId)) {
            return null;
        }
        String normalized = fallbackRequesterUserId.trim();
        if (normalized.equals(primaryRequester)) {
            return null;
        }
        return normalized;
    }
}
