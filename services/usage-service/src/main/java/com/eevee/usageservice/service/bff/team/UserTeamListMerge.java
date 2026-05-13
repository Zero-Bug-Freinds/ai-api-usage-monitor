package com.eevee.usageservice.service.bff.team;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Merges team lists resolved under different requester identifiers (e.g. JWT subject vs platform user id).
 */
public final class UserTeamListMerge {

    private UserTeamListMerge() {
    }

    /**
     * Primary list order wins; fallback adds teams whose ids were not already present.
     */
    public static List<TeamSummaryClientItem> unionByTeamId(
            List<TeamSummaryClientItem> primary,
            List<TeamSummaryClientItem> fallback
    ) {
        LinkedHashMap<String, TeamSummaryClientItem> byId = new LinkedHashMap<>();
        for (TeamSummaryClientItem t : primary) {
            byId.putIfAbsent(t.id(), t);
        }
        for (TeamSummaryClientItem t : fallback) {
            byId.putIfAbsent(t.id(), t);
        }
        return List.copyOf(byId.values());
    }
}
