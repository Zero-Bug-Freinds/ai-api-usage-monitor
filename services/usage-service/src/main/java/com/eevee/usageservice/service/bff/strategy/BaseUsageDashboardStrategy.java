package com.eevee.usageservice.service.bff.strategy;

import com.eevee.usageservice.api.dto.DailyUsagePoint;
import com.eevee.usageservice.api.dto.ModelUsageAggregate;
import com.eevee.usageservice.api.dto.MonthlyUsagePoint;
import com.eevee.usageservice.api.dto.PagedLogsResponse;
import com.eevee.usageservice.api.dto.UsageSeriesPoint;
import com.eevee.usageservice.api.dto.UsageSeriesUnit;
import com.eevee.usageservice.api.dto.UsageSummaryResponse;
import com.eevee.usageservice.api.dto.bff.TeamMemberProfile;
import com.eevee.usageservice.api.dto.bff.UsageBffDashboardResponse;
import com.eevee.usageservice.api.dto.bff.UsageDashboardEnrichment;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;

import java.util.List;

abstract class BaseUsageDashboardStrategy implements UsageDashboardStrategy {
    protected UsageBffDashboardResponse response(
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
        return new UsageBffDashboardResponse(
                mode,
                teamId,
                userId,
                teamName,
                summary,
                daily,
                monthly,
                byModel,
                usageSeries,
                usageSeriesUnit,
                logs,
                memberProfiles,
                enrichment
        );
    }
}
