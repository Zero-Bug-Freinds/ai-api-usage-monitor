package com.eevee.billingservice.api;

import com.eevee.billingservice.api.dto.ApiKeySeenResponse;
import com.eevee.billingservice.api.dto.DailyExpenditurePoint;
import com.eevee.billingservice.api.dto.ExpenditureSummaryResponse;
import com.eevee.billingservice.api.dto.MonthlyExpenditurePoint;
import com.eevee.billingservice.api.dto.TeamMonthRollupRequest;
import com.eevee.billingservice.api.dto.TeamMonthRollupResponse;
import com.eevee.billingservice.security.BillingGatewayTrustFilter;
import com.eevee.billingservice.service.ExpenditureQueryService;
import com.eevee.billingservice.service.ExpenditureTeamRollupService;
import com.eevee.usage.events.AiProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expenditure")
public class ExpenditureController {

    private final ExpenditureQueryService expenditureQueryService;
    private final ExpenditureTeamRollupService expenditureTeamRollupService;

    public ExpenditureController(
            ExpenditureQueryService expenditureQueryService,
            ExpenditureTeamRollupService expenditureTeamRollupService
    ) {
        this.expenditureQueryService = expenditureQueryService;
        this.expenditureTeamRollupService = expenditureTeamRollupService;
    }

    @GetMapping("/summary")
    public ExpenditureSummaryResponse summary(
            HttpServletRequest request,
            @RequestParam String apiKeyId,
            @RequestParam AiProvider provider,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String userId = currentUser(request);
        return expenditureQueryService.summary(userId, apiKeyId, provider, from, to);
    }

    @GetMapping("/daily")
    public List<DailyExpenditurePoint> daily(
            HttpServletRequest request,
            @RequestParam String apiKeyId,
            @RequestParam AiProvider provider,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String userId = currentUser(request);
        return expenditureQueryService.dailySeries(userId, apiKeyId, provider, from, to);
    }

    @GetMapping("/monthly")
    public List<MonthlyExpenditurePoint> monthly(
            HttpServletRequest request,
            @RequestParam String apiKeyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String userId = currentUser(request);
        return expenditureQueryService.monthlySeries(userId, apiKeyId, from, to);
    }

    @GetMapping("/api-keys")
    public List<ApiKeySeenResponse> apiKeys(
            HttpServletRequest request,
            @RequestParam(required = false) AiProvider provider
    ) {
        String userId = currentUser(request);
        return expenditureQueryService.listApiKeys(userId, provider);
    }

    /**
     * Team (or admin) view: sum {@code monthly_expenditure_agg} for many platform user ids for one calendar month.
     * Caller must only pass ids the user is allowed to see (enforced at BFF/Gateway in production).
     */
    @PostMapping("/team/month-rollup")
    public TeamMonthRollupResponse teamMonthRollup(HttpServletRequest request, @RequestBody TeamMonthRollupRequest body) {
        currentUser(request);
        return expenditureTeamRollupService.rollup(body);
    }

    private static String currentUser(HttpServletRequest request) {
        Object v = request.getAttribute(BillingGatewayTrustFilter.ATTR_USER_ID);
        if (v instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalStateException("Missing authenticated user");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException ex, WebRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
