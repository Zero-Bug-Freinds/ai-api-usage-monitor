package com.eevee.usageservice.service.bff.team;

import java.time.Instant;

public record TeamSummaryClientItem(
        String id,
        String name,
        Instant createdAt
) {
    public TeamSummaryClientItem(String id, String name, String createdAtRaw) {
        this(id, name, parseInstant(createdAtRaw));
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
