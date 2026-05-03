package com.eevee.usageservice.api.dto;

/**
 * Distinct (team, user) pair from {@code daily_usage_summary} for outbound signal fan-out.
 */
public record UsageTeamUserSlice(
        String teamId,
        String userId
) {
}
