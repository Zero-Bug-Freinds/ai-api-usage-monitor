package com.eevee.usageservice.api.dto.bff;

public record TeamMemberProfile(
        String userId,
        String displayName,
        String role
) {
}
