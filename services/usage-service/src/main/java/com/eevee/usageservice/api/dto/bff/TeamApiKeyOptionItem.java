package com.eevee.usageservice.api.dto.bff;

import java.time.Instant;

public record TeamApiKeyOptionItem(
        String id,
        String alias,
        String provider,
        Instant updatedAt,
        /** Same naming as {@link com.eevee.usageservice.domain.ApiKeyStatus} for dashboard filters. */
        String status
) {
}
