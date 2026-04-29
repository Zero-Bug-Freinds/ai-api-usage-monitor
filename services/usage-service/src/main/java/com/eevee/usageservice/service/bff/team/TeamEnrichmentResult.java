package com.eevee.usageservice.service.bff.team;

import com.eevee.usageservice.api.dto.bff.TeamMemberProfile;

import java.util.List;

public record TeamEnrichmentResult(
        String teamName,
        List<TeamMemberProfile> memberProfiles,
        List<String> warnings
) {
}
