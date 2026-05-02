package com.eevee.usageservice.service.bff.strategy;

import com.eevee.usageservice.api.dto.bff.UsageBffDashboardResponse;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;
import com.eevee.usageservice.service.bff.UsageDashboardQuery;

public interface UsageDashboardStrategy {
    UsageDashboardMode mode();

    UsageBffDashboardResponse fetch(UsageDashboardQuery query);
}
