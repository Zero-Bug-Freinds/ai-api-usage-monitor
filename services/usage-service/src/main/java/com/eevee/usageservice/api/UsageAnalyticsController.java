package com.eevee.usageservice.api;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.HourlyUsagePoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.PagedLogsResponse;
import com.eevee.usageservice.api.dto.UsageCostIntradayKpiResponse;
import com.eevee.usageservice.api.dto.UsageLogApiKeyItemResponse;
import com.eevee.usageservice.api.dto.UsageSeriesPoint;
import com.eevee.usageservice.api.dto.UsageSeriesUnit;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import com.eevee.usageservice.security.UsageGatewayTrustFilter;
import com.eevee.usageservice.service.UsageDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return dashboardService.summary(userId, from, to, provider);
    }

    @GetMapping("/dashboard/kpi/cost-intraday")
    public UsageCostIntradayKpiResponse costIntradayKpi(
            HttpServletRequest request,
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return dashboardService.costIntradayKpi(userId, provider);
    }

    @GetMapping("/dashboard/series/hourly")
    public List<HourlyUsagePoint> hourlySeries(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return dashboardService.hourlySeriesForKstDate(userId, date, provider);
    }

    @GetMapping("/dashboard/series/daily")
    public List<DailyUsagePoint> daily(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return dashboardService.dailySeries(userId, from, to, provider);
    }

    @GetMapping("/dashboard/series")
    public List<UsageSeriesPoint> series(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UsageSeriesUnit unit,
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return dashboardService.series(userId, from, to, provider, unit);
    }

    @GetMapping("/dashboard/series/monthly")
    public List<MonthlyUsagePoint> monthly(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return dashboardService.monthlySeries(userId, from, to, provider);
    }

    @GetMapping("/dashboard/by-model")
    public List<ModelUsageAggregate> byModel(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return dashboardService.byModel(userId, from, to, provider);
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String userId = currentUser(request);
        return dashboardService.logs(userId, from, to, provider, apiKeyId, requestSuccessful, model, reasoningPresence, page, size);
    }

    @GetMapping("/logs/api-keys")
    public List<UsageLogApiKeyItemResponse> logApiKeys(
            HttpServletRequest request,
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return dashboardService.logApiKeys(userId, provider);
    }

    private static String currentUser(HttpServletRequest request) {
        Object v = request.getAttribute(UsageGatewayTrustFilter.ATTR_USER_ID);
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalStateException("Missing authenticated user");
    }
}
