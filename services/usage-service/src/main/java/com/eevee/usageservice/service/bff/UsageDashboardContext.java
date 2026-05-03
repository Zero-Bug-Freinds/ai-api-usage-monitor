package com.eevee.usageservice.service.bff;

import com.eevee.usageservice.api.dto.bff.UsageBffDashboardResponse;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;
import com.eevee.usageservice.service.bff.strategy.UsageDashboardStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class UsageDashboardContext {
    private final Map<UsageDashboardMode, UsageDashboardStrategy> strategies;

    public UsageDashboardContext(List<UsageDashboardStrategy> strategyList) {
        Map<UsageDashboardMode, UsageDashboardStrategy> m = new EnumMap<>(UsageDashboardMode.class);
        for (UsageDashboardStrategy strategy : strategyList) {
            m.put(strategy.mode(), strategy);
        }
        this.strategies = m;
    }

    public UsageBffDashboardResponse fetch(UsageDashboardQuery query) {
        UsageDashboardStrategy strategy = strategies.get(query.mode());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported mode: " + query.mode());
        }
        return strategy.fetch(query);
    }
}
