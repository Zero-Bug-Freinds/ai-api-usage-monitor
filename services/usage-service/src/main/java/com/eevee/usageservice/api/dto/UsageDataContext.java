package com.eevee.usageservice.api.dto;

/**
 * Scope for personal-dashboard analytics: personal keys vs usage attributed to the user under team keys.
 */
public enum UsageDataContext {
    PERSONAL,
    TEAM_MEMBER_ONLY;

    /**
     * Parses {@code dataContext} query values ({@code PERSONAL}, {@code TEAM_MEMBER_ONLY}). Blank defaults to personal.
     */
    public static UsageDataContext fromQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return PERSONAL;
        }
        return UsageDataContext.valueOf(raw.trim().toUpperCase());
    }
}
