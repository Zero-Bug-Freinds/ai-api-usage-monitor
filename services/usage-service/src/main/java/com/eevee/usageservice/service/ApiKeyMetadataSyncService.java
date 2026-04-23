package com.eevee.usageservice.service;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyDeletedEvent;
import com.eevee.usageservice.mq.ExternalApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
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
                event.provider(),
                alias,
                mapStatus(event.status()),
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
}
