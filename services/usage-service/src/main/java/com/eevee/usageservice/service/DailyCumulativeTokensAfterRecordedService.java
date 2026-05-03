package com.eevee.usageservice.service;

import com.eevee.usage.events.DailyCumulativeTokensUpdatedEvent;
import com.eevee.usageservice.domain.UsageRecordedLogEntity;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.analytics.DailyCumulativeTokenRollupRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Updates the KST-day cumulative token rollup for the persisted log row, then emits the outbound event
 * (when {@link DailyCumulativeTokensEventPublisher} is enabled) in the same transaction as {@link UsageRecordedService#persist}.
 */
@Service
public class DailyCumulativeTokensAfterRecordedService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyCumulativeTokenRollupRepository rollupRepository;
    private final ApiKeyMetadataRepository apiKeyMetadataRepository;
    private final Optional<DailyCumulativeTokensEventPublisher> eventPublisher;

    public DailyCumulativeTokensAfterRecordedService(
            DailyCumulativeTokenRollupRepository rollupRepository,
            ApiKeyMetadataRepository apiKeyMetadataRepository,
            Optional<DailyCumulativeTokensEventPublisher> eventPublisher
    ) {
        this.rollupRepository = rollupRepository;
        this.apiKeyMetadataRepository = apiKeyMetadataRepository;
        this.eventPublisher = eventPublisher;
    }

    public void onRecorded(UsageRecordedLogEntity entity) {
        if (!rollupRepository.tryClaimProcessedEvent(entity.getEventId())) {
            return;
        }
        long delta = entity.getTotalTokens() != null ? entity.getTotalTokens() : 0L;
        if (delta < 0) {
            delta = 0L;
        }
        var usageDateKst = entity.getOccurredAt().atZone(KST).toLocalDate();
        String teamKey = entity.getTeamId() != null ? entity.getTeamId() : "";
        String apiKeyKey = entity.getApiKeyId() != null ? entity.getApiKeyId() : "";
        long dailyTotal = rollupRepository.incrementAndReturnTotal(
                usageDateKst,
                entity.getUserId(),
                teamKey,
                apiKeyKey,
                delta
        );
        String apiKeyIdPayload = apiKeyKey.isEmpty() ? null : apiKeyKey;
        String apiKeyAlias = resolveAlias(apiKeyKey);
        Instant occurredAt = Instant.now();
        var outbound = new DailyCumulativeTokensUpdatedEvent(
                DailyCumulativeTokensUpdatedEvent.CURRENT_SCHEMA_VERSION,
                entity.getEventId(),
                dailyTotal,
                entity.getUserId(),
                teamKey,
                apiKeyIdPayload,
                apiKeyAlias,
                occurredAt
        );
        eventPublisher.ifPresent(p -> p.publish(outbound));
    }

    private String resolveAlias(String apiKeyKey) {
        if (apiKeyKey == null || apiKeyKey.isEmpty()) {
            return null;
        }
        return apiKeyMetadataRepository.findById(apiKeyKey)
                .map(meta -> {
                    String a = meta.getAlias();
                    return a != null && !a.isBlank() ? a : null;
                })
                .orElse(null);
    }
}
