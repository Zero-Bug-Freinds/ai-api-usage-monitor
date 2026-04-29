package com.eevee.usageservice.service.bff.strategy;

import com.eevee.usageservice.api.dto.bff.TeamMemberProfile;
import com.eevee.usageservice.api.dto.bff.UsageBffDashboardResponse;
import com.eevee.usageservice.api.dto.bff.UsageDashboardEnrichment;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;
import com.eevee.usageservice.service.UsageDashboardService;
import com.eevee.usageservice.service.bff.UsageDashboardQuery;
import com.eevee.usageservice.service.bff.team.TeamEnrichmentResult;
import com.eevee.usageservice.service.bff.team.TeamServiceClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeamMemberUsageDashboardStrategy extends BaseUsageDashboardStrategy {
    private final UsageDashboardService usageDashboardService;
    private final TeamServiceClient teamServiceClient;

    public TeamMemberUsageDashboardStrategy(
            UsageDashboardService usageDashboardService,
            TeamServiceClient teamServiceClient
    ) {
        this.usageDashboardService = usageDashboardService;
        this.teamServiceClient = teamServiceClient;
    }

    @Override
    public UsageDashboardMode mode() {
        return UsageDashboardMode.TEAM_MEMBER;
    }

    @Override
    public UsageBffDashboardResponse fetch(UsageDashboardQuery query) {
        String teamId = require(query.teamId(), "teamId is required for TEAM_MEMBER");
        String userId = require(query.userId(), "userId is required for TEAM_MEMBER");

        TeamEnrichmentResult enrichment = teamServiceClient.loadTeamEnrichment(query.requestUserId(), teamId).join();
        List<TeamMemberProfile> selected = enrichment.memberProfiles().stream()
                .filter(p -> userId.equals(p.userId()))
                .toList();

        return response(
                mode(),
                teamId,
                userId,
                enrichment.teamName(),
                usageDashboardService.summaryByTeamAndUser(teamId, userId, query.from(), query.to(), query.provider()),
                usageDashboardService.dailySeriesByTeamAndUser(teamId, userId, query.from(), query.to(), query.provider()),
                usageDashboardService.monthlySeriesByTeamAndUser(teamId, userId, query.from(), query.to(), query.provider()),
                usageDashboardService.byModelForTeamAndUser(teamId, userId, query.from(), query.to(), query.provider()),
                usageDashboardService.logsByTeamAndUser(
                        teamId,
                        userId,
                        query.from(),
                        query.to(),
                        query.provider(),
                        null,
                        null,
                        null,
                        null,
                        query.page(),
                        query.size()
                ),
                selected,
                enrichment.warnings().isEmpty()
                        ? UsageDashboardEnrichment.ok()
                        : UsageDashboardEnrichment.partial(enrichment.warnings())
        );
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
