package com.eevee.usageservice.usage;

import com.eevee.usage.events.UsageRecordedEvent;
import org.springframework.util.StringUtils;

/**
 * Single place for deciding whether a {@link UsageRecordedEvent} should update
 * {@link com.eevee.usageservice.domain.ApiKeyMetadataScope#TEAM} metadata vs {@code PERSONAL}.
 */
public final class UsageRecordedMetadataScope {

    private UsageRecordedMetadataScope() {
    }

    /**
     * Team-scope metadata rows require an explicit team key source from the proxy, plus a consistent
     * team context after {@link UsageRecordedEventScopeNormalizer} rules.
     */
    public static boolean isTeamKeyMetadata(UsageRecordedEvent event) {
        if (event == null) {
            return false;
        }
        String normalizedTeamId = UsageRecordedEventScopeNormalizer.normalizeTeamId(event.teamId());
        String normalizedTeamApiKeyId = UsageRecordedEventScopeNormalizer.normalizeTeamApiKeyId(
                event.teamApiKeyId(),
                normalizedTeamId
        );
        if (normalizedTeamId == null || !StringUtils.hasText(normalizedTeamApiKeyId)) {
            return false;
        }
        String src = event.apiKeySource();
        return src != null && "team".equalsIgnoreCase(src.trim());
    }
}
