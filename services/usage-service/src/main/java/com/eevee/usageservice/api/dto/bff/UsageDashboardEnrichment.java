package com.eevee.usageservice.api.dto.bff;

import java.util.List;

public record UsageDashboardEnrichment(
        String status,
        boolean partial,
        List<String> warnings
) {
    public static UsageDashboardEnrichment ok() {
        return new UsageDashboardEnrichment("OK", false, List.of());
    }

    public static UsageDashboardEnrichment partial(List<String> warnings) {
        return new UsageDashboardEnrichment("PARTIAL", true, warnings);
    }
}
