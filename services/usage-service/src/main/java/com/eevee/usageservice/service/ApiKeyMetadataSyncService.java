package com.eevee.usageservice.service;

import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyDeletedEvent;
import com.eevee.usageservice.mq.ExternalApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletedEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionCancelledEvent;
import com.eevee.usageservice.mq.TeamApiKeyDeletionScheduledEvent;
import com.eevee.usageservice.mq.TeamApiKeyRegisteredEvent;
import com.eevee.usageservice.mq.TeamApiKeyStatus;
import com.eevee.usageservice.mq.TeamApiKeyStatusChangedEvent;
import com.eevee.usageservice.mq.TeamApiKeyUpdatedEvent;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import com.eevee.usageservice.repository.UsageRecordedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class ApiKeyMetadataSyncService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyMetadataSyncService.class);

    private final ApiKeyMetadataRepository apiKeyMetadataRepository;
    private final UsageRecordedLogRepository usageRecordedLogRepository;

    public ApiKeyMetadataSyncService(
            ApiKeyMetadataRepository apiKeyMetadataRepository,
            UsageRecordedLogRepository usageRecordedLogRepository
    ) {
        this.apiKeyMetadataRepository = apiKeyMetadataRepository;
        this.usageRecordedLogRepository = usageRecordedLogRepository;
    }

    @Transactional
    public void upsertFromIdentity(ExternalApiKeyStatusChangedEvent event) {
        if (event.keyId() == null || event.userId() == null || event.status() == null) {
            throw new IllegalArgumentException("keyId, userId, status are required");
        }
        String keyId = String.valueOf(event.keyId());
        String userId = String.valueOf(event.userId());
        Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(keyId)
                .orElseGet(() -> ApiKeyMetadataEntity.create(keyId, userId));

        String alias = StringUtils.hasText(event.alias()) ? event.alias().trim() : entity.getAlias();
        entity.apply(
                userId,
                entity.getTeamId(),
                event.provider(),
                alias,
                mapStatus(event.status()),
                updatedAt
        );
        apiKeyMetadataRepository.save(entity);
    }

    @Transactional
    public void upsertFromTeamRegistered(TeamApiKeyRegisteredEvent event) {
        upsertTeamMetadata(event.apiKeyId(), event.teamId(), event.actorUserId(), event.provider(), event.alias(), ApiKeyStatus.ACTIVE, event.occurredAt());
    }

    @Transactional
    public void upsertFromTeamUpdated(TeamApiKeyUpdatedEvent event) {
        upsertTeamMetadata(event.apiKeyId(), event.teamId(), event.actorUserId(), event.provider(), event.alias(), ApiKeyStatus.ACTIVE, event.occurredAt());
    }

    @Transactional
    public void handleTeamDeleted(TeamApiKeyDeletedEvent event) {
        upsertTeamMetadata(event.apiKeyId(), event.teamId(), event.actorUserId(), event.provider(), event.alias(), ApiKeyStatus.DELETED, event.occurredAt());
    }

    @Transactional
    public void handleTeamDeletionScheduled(TeamApiKeyDeletionScheduledEvent event) {
        upsertTeamMetadata(event.apiKeyId(), event.teamId(), event.actorUserId(), event.provider(), event.alias(), ApiKeyStatus.DELETION_REQUESTED, event.occurredAt());
    }

    @Transactional
    public void handleTeamDeletionCancelled(TeamApiKeyDeletionCancelledEvent event) {
        upsertTeamMetadata(event.apiKeyId(), event.teamId(), event.actorUserId(), event.provider(), event.alias(), ApiKeyStatus.ACTIVE, event.occurredAt());
    }

    @Transactional
    public void upsertFromTeamStatusChanged(TeamApiKeyStatusChangedEvent event) {
        upsertTeamMetadata(
                event.teamApiKeyId(),
                event.teamId(),
                event.ownerUserId(),
                event.provider(),
                event.alias(),
                mapTeamStatus(event.status()),
                event.occurredAt()
        );
    }

    @Transactional
    public void upsertFromUsageRecordedEvent(UsageRecordedEvent event) {
        if (event == null || !StringUtils.hasText(event.apiKeyId())) {
            return;
        }
        String keyId = event.apiKeyId().trim();
        ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(keyId)
                .orElseGet(() -> ApiKeyMetadataEntity.create(keyId, resolveUserId(event, keyId)));

        String userId = resolveUserId(event, keyId);
        String alias = StringUtils.hasText(event.apiKeyAlias()) ? event.apiKeyAlias().trim() : entity.getAlias();
        String provider = resolveProvider(event, entity);
        String teamId = StringUtils.hasText(event.teamId()) ? event.teamId().trim() : entity.getTeamId();
        Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        entity.apply(
                userId,
                teamId,
                provider,
                alias,
                entity.getStatus() != null ? entity.getStatus() : ApiKeyStatus.ACTIVE,
                updatedAt
        );
        apiKeyMetadataRepository.save(entity);
    }

    /**
     * 물리 삭제 완료 이벤트: 기본은 메타데이터만 {@link ApiKeyStatus#DELETED} 로 맞추고 사용 로그는 유지한다.
     * {@code retainLogs == false} 일 때만 해당 키의 사용 로그와 메타데이터 행을 삭제한다.
     */
    @Transactional
    public void handleExternalApiKeyDeleted(ExternalApiKeyDeletedEvent event) {
        if (event.apiKeyId() == null || event.userId() == null) {
            throw new IllegalArgumentException("apiKeyId, userId are required");
        }
        String keyId = String.valueOf(event.apiKeyId());
        String userId = String.valueOf(event.userId());
        boolean retainLogs = event.retainLogs() == null || event.retainLogs();

        if (!retainLogs) {
            int removedLogs = usageRecordedLogRepository.deleteByApiKeyId(keyId);
            apiKeyMetadataRepository.deleteById(keyId);
            log.info(
                    "External API key purged from usage (retainLogs=false) keyId={} userId={} removedLogs={}",
                    keyId,
                    userId,
                    removedLogs
            );
            return;
        }

        Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(keyId)
                .orElseGet(() -> ApiKeyMetadataEntity.create(keyId, userId));

        String alias = StringUtils.hasText(event.alias()) ? event.alias().trim() : entity.getAlias();
        String provider = StringUtils.hasText(event.provider()) ? event.provider().trim() : entity.getProvider();

        entity.apply(
                userId,
                entity.getTeamId(),
                provider,
                alias,
                ApiKeyStatus.DELETED,
                updatedAt
        );
        apiKeyMetadataRepository.save(entity);
    }

    private static ApiKeyStatus mapStatus(ExternalApiKeyStatus status) {
        return switch (status) {
            case ACTIVE -> ApiKeyStatus.ACTIVE;
            case DELETION_REQUESTED -> ApiKeyStatus.DELETION_REQUESTED;
            case DELETED -> ApiKeyStatus.DELETED;
        };
    }

    private static ApiKeyStatus mapTeamStatus(TeamApiKeyStatus status) {
        if (status == null) {
            return ApiKeyStatus.ACTIVE;
        }
        return switch (status) {
            case ACTIVE -> ApiKeyStatus.ACTIVE;
            case DELETION_REQUESTED -> ApiKeyStatus.DELETION_REQUESTED;
            case DELETED -> ApiKeyStatus.DELETED;
        };
    }

    private void upsertTeamMetadata(
            Long keyIdRaw,
            Long teamIdRaw,
            String ownerUserId,
            String provider,
            String alias,
            ApiKeyStatus status,
            Instant occurredAt
    ) {
        if (keyIdRaw == null) {
            throw new IllegalArgumentException("team api key id is required");
        }
        String keyId = String.valueOf(keyIdRaw);
        Instant updatedAt = occurredAt != null ? occurredAt : Instant.now();
        ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(keyId).orElse(null);

        String resolvedUserId = StringUtils.hasText(ownerUserId)
                ? ownerUserId.trim()
                : (entity != null ? entity.getUserId() : null);
        if (!StringUtils.hasText(resolvedUserId)) {
            log.warn("Skipping team API key metadata upsert due to missing owner userId keyId={}", keyId);
            return;
        }

        ApiKeyMetadataEntity target = entity != null ? entity : ApiKeyMetadataEntity.create(keyId, resolvedUserId);
        String resolvedTeamId = teamIdRaw != null
                ? String.valueOf(teamIdRaw)
                : target.getTeamId();
        String resolvedAlias = StringUtils.hasText(alias) ? alias.trim() : target.getAlias();
        String resolvedProvider = StringUtils.hasText(provider) ? provider.trim() : target.getProvider();
        target.apply(
                resolvedUserId,
                resolvedTeamId,
                resolvedProvider,
                resolvedAlias,
                status,
                updatedAt
        );
        apiKeyMetadataRepository.save(target);
    }

    private static String resolveUserId(UsageRecordedEvent event, String keyId) {
        if (!StringUtils.hasText(event.userId())) {
            throw new IllegalArgumentException("usage event userId is required for keyId=" + keyId);
        }
        return event.userId().trim();
    }

    private static String resolveProvider(UsageRecordedEvent event, ApiKeyMetadataEntity entity) {
        if (event.provider() != null) {
            return event.provider().name();
        }
        return entity.getProvider();
    }
}
