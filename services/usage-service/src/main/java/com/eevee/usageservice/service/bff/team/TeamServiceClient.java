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
    private final boolean cacheEmptyTeamList;
    private final boolean diagnosticsLogging;

    public TeamServiceClient(
            UsageServiceProperties properties,
            @Qualifier("usageBffExecutor") Executor usageBffExecutor
    ) {
        UsageServiceProperties.Team team = properties.getTeam();
        this.restClient = RestClient.builder()
                .baseUrl(team.getBaseUrl())
                .build();
        this.usageBffExecutor = usageBffExecutor;
        this.userTeamsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(60, team.getTeamListCacheTtlSeconds())))
                .maximumSize(2_000)
                .build();
        this.memberCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(60, team.getMemberCacheTtlSeconds())))
                .maximumSize(1_000)
                .build();
        this.teamServiceCircuitBreaker = CircuitBreaker.ofDefaults("usage-team-service");
        this.timeoutMs = team.getTimeoutMs();
        this.cacheEmptyTeamList = team.isCacheEmptyTeamList();
        this.diagnosticsLogging = team.isDiagnosticsLogging();
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
        List<TeamSummaryClientItem> primary = fetchTeamsForRequester(primaryRequester, "primary");
        List<TeamSummaryClientItem> result;
        List<TeamSummaryClientItem> fallbackList = null;
        if (fallbackRequester == null) {
            result = primary;
        } else {
            fallbackList = fetchTeamsForRequester(fallbackRequester, "fallback");
            result = UserTeamListMerge.unionByTeamId(primary, fallbackList);
            log.debug(
                    "fetchUserTeams merged primaryCount={} fallbackCount={} total={}",
                    primary.size(),
                    fallbackList.size(),
                    result.size()
            );
        }
        boolean cachePut = !result.isEmpty() || cacheEmptyTeamList;
        if (cachePut) {
            userTeamsCache.put(cacheKey, result);
        }
        logTeamListDiagnostics(
                primaryRequester,
                fallbackRequester,
                primary,
                fallbackList,
                result,
                cachePut
        );
        if (result.isEmpty()) {
            log.warn(
                    "No teams returned from team-service primaryMasked={} primaryKind={} fallbackMasked={} "
                            + "(check JWT subject vs userId claim alignment with team-service memberships)",
                    maskUserIdForLog(primaryRequester),
                    userIdKind(primaryRequester),
                    fallbackRequester != null ? maskUserIdForLog(fallbackRequester) : "(none)"
            );
        }
        return result;
    }

    private void logTeamListDiagnostics(
            String primaryRequester,
            String fallbackRequester,
            List<TeamSummaryClientItem> primary,
            List<TeamSummaryClientItem> fallbackList,
            List<TeamSummaryClientItem> merged,
            boolean cachePut
    ) {
        String fbMasked = fallbackRequester != null ? maskUserIdForLog(fallbackRequester) : "(none)";
        String fbKind = fallbackRequester != null ? userIdKind(fallbackRequester) : "none";
        int fbSize = fallbackList != null ? fallbackList.size() : 0;
        String line = String.format(
                "teamListFetch primaryKind=%s primaryMasked=%s fallbackKind=%s fallbackMasked=%s "
                        + "primarySize=%d fallbackSize=%d mergedSize=%d cachePut=%s",
                userIdKind(primaryRequester),
                maskUserIdForLog(primaryRequester),
                fbKind,
                fbMasked,
                primary.size(),
                fbSize,
                merged.size(),
                cachePut
        );
        if (diagnosticsLogging) {
            log.info(line);
        } else {
            log.debug(line);
        }
    }

    /** Minimal PII: email-like → short local prefix + masked domain hint; else prefix of opaque id. */
    static String maskUserIdForLog(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "(empty)";
        }
        String s = raw.trim();
        int at = s.indexOf('@');
        if (at > 0 && at < s.length() - 1) {
            String local = s.substring(0, at);
            String domain = s.substring(at + 1).trim();
            if (!domain.isEmpty()) {
                String localMask = local.length() <= 2 ? "**" : local.substring(0, Math.min(2, local.length())) + "***";
                int dot = domain.lastIndexOf('.');
                String domainMask = dot > 0 && dot < domain.length() - 1
                        ? domain.charAt(0) + "***." + domain.substring(dot + 1)
                        : (domain.length() > 0 ? domain.charAt(0) + "***" : "*");
                return localMask + "@" + domainMask;
            }
        }
        if (s.length() <= 4) {
            return "***";
        }
        return s.substring(0, 4) + "***";
    }

    static String userIdKind(String raw) {
        return StringUtils.hasText(raw) && raw.trim().contains("@") ? "EMAIL_LIKE" : "OPAQUE_ID";
    }

    private List<TeamSummaryClientItem> fetchTeamsForRequester(String requesterUserId, String roleInLog) {
        try {
            return callWithCircuitBreaker(() -> fetchUserTeamsInternal(requesterUserId));
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to fetch teams from team-service role={} maskedRequester={} reason={}",
                    roleInLog,
                    maskUserIdForLog(requesterUserId),
                    ex.getMessage()
            );
            return List.of();
        }
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
        log.debug(
                "team-service GET /internal/v1/users/*/teams maskedUserId={}",
                maskUserIdForLog(requesterUserId)
        );
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
            String id = extractTeamListItemId(n);
            String name = extractTeamListItemName(n);
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

    /** Accepts string or numeric {@code id} from team-service JSON. */
    private static String extractTeamListItemId(JsonNode n) {
        JsonNode idNode = n.path("id");
        if (idNode.isMissingNode() || idNode.isNull()) {
            return null;
        }
        if (idNode.isNumber()) {
            return Long.toString(idNode.longValue());
        }
        return nullIfBlank(idNode.asText(null));
    }

    private static String extractTeamListItemName(JsonNode n) {
        return nullIfBlank(n.path("name").asText(null));
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
