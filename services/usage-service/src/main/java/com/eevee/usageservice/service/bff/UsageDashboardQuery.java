package com.eevee.usageservice.service.bff;

import com.eevee.usage.events.AiProvider;
import com.eevee.usageservice.api.dto.bff.UsageDashboardMode;

import java.time.LocalDate;

public record UsageDashboardQuery(
        UsageDashboardMode mode,
        String requestUserId,
        String teamId,
        String userId,
        LocalDate from,
        LocalDate to,
        AiProvider provider,
        String apiKeyId,
        int page,
        int size
) {
}
