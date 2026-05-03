package com.eevee.usageservice.api;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.bff.UsageBffDashboardResponse;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;
import com.eevee.usageservice.security.UsageGatewayTrustFilter;
import com.eevee.usageservice.service.bff.UsageDashboardContext;
import com.eevee.usageservice.service.bff.UsageDashboardQuery;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/usage/bff")
public class UsageBffDashboardController {
    private final UsageDashboardContext dashboardContext;

    public UsageBffDashboardController(UsageDashboardContext dashboardContext) {
        this.dashboardContext = dashboardContext;
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
        String requester = currentUser(request);
        UsageDashboardMode resolvedMode = resolveMode(mode, teamId, userId);
        return dashboardContext.fetch(new UsageDashboardQuery(
                resolvedMode,
                requester,
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

    private static UsageDashboardMode resolveMode(String modeRaw, String teamIdRaw, String userIdRaw) {
        String teamId = blankToNull(teamIdRaw);
        String userId = blankToNull(userIdRaw);
        if (modeRaw != null && !modeRaw.isBlank()) {
            UsageDashboardMode explicit = UsageDashboardMode.valueOf(modeRaw.trim().toUpperCase());
            if (explicit == UsageDashboardMode.PERSONAL && (teamId != null || userId != null)) {
                throw new IllegalArgumentException("PERSONAL mode does not accept teamId/userId");
            }
            if (explicit == UsageDashboardMode.TEAM_TOTAL && teamId == null) {
                throw new IllegalArgumentException("TEAM_TOTAL mode requires teamId");
            }
            if (explicit == UsageDashboardMode.TEAM_MEMBER && (teamId == null || userId == null)) {
                throw new IllegalArgumentException("TEAM_MEMBER mode requires teamId and userId");
            }
            return explicit;
        }
        if (teamId == null) {
            if (userId != null) {
                throw new IllegalArgumentException("userId without teamId is not allowed");
            }
            return UsageDashboardMode.PERSONAL;
        }
        return userId == null ? UsageDashboardMode.TEAM_TOTAL : UsageDashboardMode.TEAM_MEMBER;
    }

    private static String currentUser(HttpServletRequest request) {
        Object v = request.getAttribute(UsageGatewayTrustFilter.ATTR_USER_ID);
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
