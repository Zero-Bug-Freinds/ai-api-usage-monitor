package com.eevee.usageservice.service.bff.strategy;

import com.eevee.usageservice.api.dto.bff.UsageBffDashboardResponse;
import com.eevee.usageservice.api.dto.bff.UsageDashboardEnrichment;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;
import com.eevee.usageservice.service.UsageDashboardService;
import com.eevee.usageservice.service.bff.UsageDashboardQuery;
import com.eevee.usageservice.service.bff.team.TeamEnrichmentResult;
import com.eevee.usageservice.service.bff.team.TeamServiceClient;
import org.springframework.stereotype.Component;

@Component
public class TeamTotalUsageDashboardStrategy extends BaseUsageDashboardStrategy {
    private final UsageDashboardService usageDashboardService;
    private final TeamServiceClient teamServiceClient;

    public TeamTotalUsageDashboardStrategy(
            UsageDashboardService usageDashboardService,
            TeamServiceClient teamServiceClient
    ) {
        this.usageDashboardService = usageDashboardService;
        this.teamServiceClient = teamServiceClient;
    }

    @Override
    public UsageDashboardMode mode() {
        return UsageDashboardMode.TEAM_TOTAL;
    }

    @Override
    public UsageBffDashboardResponse fetch(UsageDashboardQuery query) {
        String teamId = requireTeamId(query);
        TeamEnrichmentResult enrichment = teamServiceClient.loadTeamEnrichment(query.requestUserId(), teamId).join();
        UsageDashboardService.TeamUsageSeriesBundle seriesBundle = usageDashboardService.teamUsageSeriesForBff(
                teamId,
                query.from(),
                query.to(),
                query.provider(),
                query.apiKeyId()
        );
        return response(
                mode(),
                teamId,
                null,
                enrichment.teamName(),
                usageDashboardService.summaryByTeam(teamId, query.from(), query.to(), query.provider(), query.apiKeyId()),
                usageDashboardService.dailySeriesByTeam(teamId, query.from(), query.to(), query.provider(), query.apiKeyId()),
                usageDashboardService.monthlySeriesByTeam(teamId, query.from(), query.to(), query.provider(), query.apiKeyId()),
                usageDashboardService.byModelForTeam(teamId, query.from(), query.to(), query.provider(), query.apiKeyId()),
                seriesBundle.points(),
                seriesBundle.unit(),
                usageDashboardService.logsByTeam(
                        teamId,
                        query.from(),
                        query.to(),
                        query.provider(),
                        query.apiKeyId(),
                        null,
                        null,
                        null,
                        query.page(),
                        query.size()
                ),
                enrichment.memberProfiles(),
                enrichment.warnings().isEmpty()
                        ? UsageDashboardEnrichment.ok()
                        : UsageDashboardEnrichment.partial(enrichment.warnings())
        );
    }

    private static String requireTeamId(UsageDashboardQuery query) {
        if (query.teamId() == null || query.teamId().isBlank()) {
            throw new IllegalArgumentException("teamId is required for TEAM_TOTAL");
        }
        return query.teamId();
    }
}
