package com.eevee.usageservice.usage;

/**
 * Shared rules for interpreting {@code teamId} and {@code teamApiKeyId} from {@link com.eevee.usage.events.UsageRecordedEvent}
 * so that {@link com.eevee.usageservice.service.UsageRecordedService} (log row) and
 * {@link com.eevee.usageservice.service.ApiKeyMetadataSyncService} (metadata upsert) stay aligned for normal team traffic.
 */
public final class UsageRecordedEventScopeNormalizer {

    private UsageRecordedEventScopeNormalizer() {
    }

    public static String normalizeTeamId(String teamId) {
        if (teamId == null) {
            return null;
        }
        String normalized = teamId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Team API key id is only persisted when a team context exists; without {@code normalizedTeamId}, the log stores null
     * for {@code team_api_key_id} even if the raw event carried a team key id string.
     */
    public static String normalizeTeamApiKeyId(String teamApiKeyId, String normalizedTeamId) {
        if (normalizedTeamId == null) {
            return null;
        }
        if (teamApiKeyId == null) {
            return null;
        }
        String normalized = teamApiKeyId.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
