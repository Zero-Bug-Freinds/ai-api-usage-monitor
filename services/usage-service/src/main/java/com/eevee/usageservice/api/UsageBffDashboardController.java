package com.eevee.usageservice.api;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.bff.TeamApiKeyOptionResponse;
import com.eevee.usageservice.api.dto.bff.TeamSummaryOptionResponse;
import com.eevee.usageservice.api.dto.bff.UsageBffDashboardResponse;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;
import com.eevee.usageservice.security.UsageGatewayTrustFilter;
import com.eevee.usageservice.service.bff.UsageDashboardContext;
import com.eevee.usageservice.service.bff.UsageDashboardQuery;
import com.eevee.usageservice.config.UsageServiceProperties;
import com.eevee.usageservice.service.bff.team.TeamBffQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/usage/bff")
public class UsageBffDashboardController {

    private static final Logger log = LoggerFactory.getLogger(UsageBffDashboardController.class);

    private final UsageDashboardContext dashboardContext;
    private final TeamBffQueryService teamBffQueryService;
    private final UsageServiceProperties usageServiceProperties;

    public UsageBffDashboardController(
            UsageDashboardContext dashboardContext,
            TeamBffQueryService teamBffQueryService,
            UsageServiceProperties usageServiceProperties
    ) {
        this.dashboardContext = dashboardContext;
        this.teamBffQueryService = teamBffQueryService;
        this.usageServiceProperties = usageServiceProperties;
    }

    @GetMapping("/dashboard")
    public UsageBffDashboardResponse dashboard(
            HttpServletRequest request,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Requester requester = currentRequester(request);
        UsageDashboardMode resolvedMode = resolveMode(mode, teamId, userId, apiKeyId);
        return dashboardContext.fetch(new UsageDashboardQuery(
                resolvedMode,
                requester.userId(),
                blankToNull(teamId),
                blankToNull(userId),
                from,
                to,
                provider,
                blankToNull(apiKeyId),
                page,
                size
        ));
    }

    @GetMapping("/teams")
    public TeamSummaryOptionResponse teams(HttpServletRequest request) {
        Requester requester = currentRequester(request);
        if (usageServiceProperties.getTeam().isDiagnosticsLogging()) {
            log.info(
                    "bff GET /teams headerDerived hasPlatformUserId={}",
                    requester.platformUserId() != null
            );
        }
        return new TeamSummaryOptionResponse(
                teamBffQueryService.loadTeams(requester.userId(), requester.platformUserId())
        );
    }

    @GetMapping("/teams/{teamId}/api-keys")
    public TeamApiKeyOptionResponse teamApiKeys(
            HttpServletRequest request,
            @PathVariable String teamId
    ) {
        Requester requester = currentRequester(request);
        return new TeamApiKeyOptionResponse(
                teamBffQueryService.loadTeamApiKeys(requester.userId(), blankToNull(teamId))
        );
    }

    private static UsageDashboardMode resolveMode(String modeRaw, String teamIdRaw, String userIdRaw, String apiKeyIdRaw) {
        String teamId = blankToNull(teamIdRaw);
        String userId = blankToNull(userIdRaw);
        String apiKeyId = blankToNull(apiKeyIdRaw);
        if (modeRaw != null && !modeRaw.isBlank()) {
            UsageDashboardMode explicit = UsageDashboardMode.valueOf(modeRaw.trim().toUpperCase());
            if (explicit == UsageDashboardMode.PERSONAL && (teamId != null || userId != null || apiKeyId != null)) {
                throw new IllegalArgumentException("PERSONAL mode does not accept teamId/userId/apiKeyId");
            }
            if (explicit == UsageDashboardMode.TEAM_TOTAL && (teamId == null || userId != null)) {
                throw new IllegalArgumentException("TEAM_TOTAL mode requires teamId and disallows userId");
            }
            if (explicit == UsageDashboardMode.TEAM_MEMBER && (teamId == null || userId == null || apiKeyId != null)) {
                throw new IllegalArgumentException("TEAM_MEMBER mode requires teamId/userId and disallows apiKeyId");
            }
            return explicit;
        }
        if (teamId == null) {
            if (userId != null || apiKeyId != null) {
                throw new IllegalArgumentException("PERSONAL mode does not accept team/member filters");
            }
            return UsageDashboardMode.PERSONAL;
        }
        if (userId != null && apiKeyId != null) {
            throw new IllegalArgumentException("TEAM_MEMBER mode does not accept apiKeyId");
        }
        return userId == null ? UsageDashboardMode.TEAM_TOTAL : UsageDashboardMode.TEAM_MEMBER;
    }

    private static Requester currentRequester(HttpServletRequest request) {
        Object v = request.getAttribute(UsageGatewayTrustFilter.ATTR_USER_ID);
        if (v instanceof String s && !s.isBlank()) {
            Object platform = request.getAttribute(UsageGatewayTrustFilter.ATTR_PLATFORM_USER_ID);
            String platformUserId = (platform instanceof String p && !p.isBlank()) ? p : null;
            return new Requester(s, platformUserId);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record Requester(String userId, String platformUserId) {
    }
}
