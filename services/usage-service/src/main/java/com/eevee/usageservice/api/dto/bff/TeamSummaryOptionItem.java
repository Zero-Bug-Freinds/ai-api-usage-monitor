package com.eevee.usageservice.api.dto.bff;

import java.time.Instant;

public record TeamSummaryOptionItem(
        String id,
        String name,
        Instant createdAt
) {
}
