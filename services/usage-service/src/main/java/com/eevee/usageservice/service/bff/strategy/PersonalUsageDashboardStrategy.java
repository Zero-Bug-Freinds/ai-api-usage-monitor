package com.eevee.usageservice.service.bff.strategy;

import com.eevee.usageservice.api.dto.bff.UsageBffDashboardResponse;
import com.eevee.usageservice.api.dto.bff.UsageDashboardEnrichment;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;
import com.eevee.usageservice.service.UsageDashboardService;
import com.eevee.usageservice.service.bff.UsageDashboardQuery;
import org.springframework.stereotype.Component;

@Component
public class PersonalUsageDashboardStrategy extends BaseUsageDashboardStrategy {
    private final UsageDashboardService usageDashboardService;

    public PersonalUsageDashboardStrategy(UsageDashboardService usageDashboardService) {
        this.usageDashboardService = usageDashboardService;
    }

    @Override
    public UsageDashboardMode mode() {
        return UsageDashboardMode.PERSONAL;
    }

    @Override
    public UsageBffDashboardResponse fetch(UsageDashboardQuery query) {
        String userId = query.requestUserId();
        return response(
                mode(),
                null,
                userId,
                null,
                usageDashboardService.summary(userId, query.from(), query.to(), query.provider()),
                usageDashboardService.dailySeries(userId, query.from(), query.to(), query.provider()),
                usageDashboardService.monthlySeries(userId, query.from(), query.to(), query.provider()),
                usageDashboardService.byModel(userId, query.from(), query.to(), query.provider()),
                usageDashboardService.logs(
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
                java.util.List.of(),
                UsageDashboardEnrichment.ok()
        );
    }
}
