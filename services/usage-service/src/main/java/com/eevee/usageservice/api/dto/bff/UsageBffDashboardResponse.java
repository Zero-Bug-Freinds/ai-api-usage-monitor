package com.eevee.usageservice.api.dto.bff;

import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.PagedLogsResponse;
import com.eevee.usageservice.api.dto.UsageSeriesPoint;
import com.eevee.usageservice.api.dto.UsageSeriesUnit;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;

import java.util.List;

public record UsageBffDashboardResponse(
        UsageDashboardMode mode,
        String teamId,
        String userId,
        String teamName,
        UsageSummaryResponse summary,
        List<DailyUsagePoint> daily,
        List<MonthlyUsagePoint> monthly,
        List<ModelUsageAggregate> byModel,
        List<UsageSeriesPoint> usageSeries,
        UsageSeriesUnit usageSeriesUnit,
        PagedLogsResponse logs,
        List<TeamMemberProfile> memberProfiles,
        UsageDashboardEnrichment enrichment
) {
}
