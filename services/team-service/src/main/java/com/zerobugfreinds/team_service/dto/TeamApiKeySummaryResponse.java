package com.zerobugfreinds.team_service.dto;

import java.time.Instant;

public record TeamApiKeySummaryResponse(
        Long id,
        String provider,
        String alias,
        String keyPreview,
        Instant createdAt
) {
}
