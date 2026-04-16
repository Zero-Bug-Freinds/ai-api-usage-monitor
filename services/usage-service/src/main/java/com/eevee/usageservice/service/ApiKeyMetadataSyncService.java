package com.eevee.usageservice.service;

import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyStatus;
import com.eevee.usageservice.mq.ExternalApiKeyStatusChangedEvent;
import com.eevee.usageservice.repository.ApiKeyMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class ApiKeyMetadataSyncService {

    private final ApiKeyMetadataRepository apiKeyMetadataRepository;

    public ApiKeyMetadataSyncService(ApiKeyMetadataRepository apiKeyMetadataRepository) {
        this.apiKeyMetadataRepository = apiKeyMetadataRepository;
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

    private static ApiKeyStatus mapStatus(ExternalApiKeyStatus status) {
        return switch (status) {
            case ACTIVE -> ApiKeyStatus.ACTIVE;
            case DELETION_REQUESTED -> ApiKeyStatus.DELETION_REQUESTED;
            case DELETED -> ApiKeyStatus.DELETED;
        };
    }
}
