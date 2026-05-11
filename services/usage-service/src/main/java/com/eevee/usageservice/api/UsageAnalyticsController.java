package com.eevee.usageservice.api;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.HourlyUsagePoint;
import com.eevee.usageservice.api.dto.LatencyInsightResponse;
import com.eevee.usageservice.api.dto.LatencyStabilityPoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.PagedLogsResponse;
import com.eevee.usageservice.api.dto.UsageCostIntradayKpiResponse;
import com.eevee.usageservice.api.dto.UsageDataContext;
import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.api.dto.UsageSeriesPoint;
import com.eevee.usageservice.api.dto.UsageSeriesUnit;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import com.eevee.usageservice.security.UsageGatewayTrustFilter;
import com.eevee.usageservice.service.UsageDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageAnalyticsController {

    private final UsageDashboardService dashboardService;

    public UsageAnalyticsController(UsageDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard/summary")
    public UsageSummaryResponse summary(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.summary(userId, from, to, provider, ctx, apiKeyId, teamId);
    }

    @GetMapping("/dashboard/kpi/cost-intraday")
    public UsageCostIntradayKpiResponse costIntradayKpi(
            HttpServletRequest request,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.costIntradayKpi(userId, provider, ctx, apiKeyId, teamId);
    }

    @GetMapping("/dashboard/series/hourly")
    public List<HourlyUsagePoint> hourlySeries(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.hourlySeriesForKstDate(userId, date, provider, ctx, apiKeyId, teamId);
    }

    @GetMapping("/dashboard/series/daily")
    public List<DailyUsagePoint> daily(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.dailySeries(userId, from, to, provider, ctx, apiKeyId, teamId);
    }

    @GetMapping("/dashboard/series")
    public List<UsageSeriesPoint> series(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UsageSeriesUnit unit,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.series(userId, from, to, provider, unit, ctx, apiKeyId, teamId);
    }

    @GetMapping("/dashboard/series/latency-stability")
    public List<LatencyStabilityPoint> latencyStabilitySeries(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UsageSeriesUnit unit,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.latencyStabilitySeries(userId, from, to, provider, unit, ctx, apiKeyId, teamId);
    }

    @GetMapping("/dashboard/kpi/latency-insight")
    public LatencyInsightResponse latencyInsight(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.latencyInsight(userId, from, to, provider, ctx, apiKeyId, teamId);
    }

    @GetMapping("/dashboard/series/monthly")
    public List<MonthlyUsagePoint> monthly(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.monthlySeries(userId, from, to, provider, ctx, apiKeyId, teamId);
    }

    @GetMapping("/dashboard/by-model")
    public List<ModelUsageAggregate> byModel(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) String teamId
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.byModel(userId, from, to, provider, ctx, apiKeyId, teamId);
    }

    @GetMapping("/logs")
    public PagedLogsResponse logs(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String apiKeyId,
            @RequestParam(required = false) Boolean requestSuccessful,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String reasoningPresence,
            @RequestParam(required = false) String dataContext,
            @RequestParam(required = false) String teamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String userId = currentUser(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.logs(
                userId,
                from,
                to,
                provider,
                apiKeyId,
                requestSuccessful,
                model,
                reasoningPresence,
                page,
                size,
                ctx,
                teamId
        );
    }

    @GetMapping("/logs/api-keys")
    public List<UsageLogApiKeyItemResponse> logApiKeys(
            HttpServletRequest request,
            @RequestParam(required = false) AiProvider provider,
            @RequestParam(required = false) String dataContext
    ) {
        String userId = currentUser(request);
        String alternateSubject = alternatePersonalSubjectUserId(request);
        UsageDataContext ctx = parseDataContext(dataContext);
        return dashboardService.logApiKeys(userId, alternateSubject, provider, ctx);
    }

    private static UsageDataContext parseDataContext(String raw) {
        if (raw == null || raw.isBlank()) {
            return UsageDataContext.PERSONAL;
        }
        try {
            return UsageDataContext.fromQuery(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dataContext");
        }
    }

    private static String currentUser(HttpServletRequest request) {
        Object v = request.getAttribute(UsageGatewayTrustFilter.ATTR_USER_ID);
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user");
    }

    /**
     * When identity-synced metadata rows use a different subject than {@link #currentUser(HttpServletRequest)}
     * (e.g. opaque id vs email), merge those keys into the personal alias list.
     */
    private static String alternatePersonalSubjectUserId(HttpServletRequest request) {
        Object p = request.getAttribute(UsageGatewayTrustFilter.ATTR_PLATFORM_USER_ID);
        return (p instanceof String s && !s.isBlank()) ? s.trim() : null;
    }
}
