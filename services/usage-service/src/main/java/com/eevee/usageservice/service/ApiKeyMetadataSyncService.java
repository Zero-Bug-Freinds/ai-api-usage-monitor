package com.eevee.usageservice.service;

import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usageservice.domain.ApiKeyMetadataEntity;
import com.eevee.usageservice.domain.ApiKeyMetadataEntityId;
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
import com.eevee.usageservice.service.bff.team.TeamServiceClient;
import com.eevee.usageservice.usage.UsageRecordedMetadataScope;
import com.eevee.usageservice.usage.UsageRecordedEventScopeNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApiKeyMetadataSyncService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyMetadataSyncService.class);

    private final ApiKeyMetadataRepository apiKeyMetadataRepository;
    private final UsageRecordedLogRepository usageRecordedLogRepository;
    private final TeamServiceClient teamServiceClient;

    public ApiKeyMetadataSyncService(
            ApiKeyMetadataRepository apiKeyMetadataRepository,
            UsageRecordedLogRepository usageRecordedLogRepository,
            TeamServiceClient teamServiceClient
    ) {
        this.apiKeyMetadataRepository = apiKeyMetadataRepository;
        this.usageRecordedLogRepository = usageRecordedLogRepository;
        this.teamServiceClient = teamServiceClient;
    }

    @Transactional
    public void upsertFromIdentity(ExternalApiKeyStatusChangedEvent event) {
        if (event.keyId() == null || event.userId() == null || event.status() == null) {
            throw new IllegalArgumentException("keyId, userId, status are required");
        }
        String keyId = String.valueOf(event.keyId());
        String userId = String.valueOf(event.userId());
        Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        var id = ApiKeyMetadataEntityId.personal(keyId, userId);
        ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(id)
                .orElseGet(() -> ApiKeyMetadataEntity.createPersonal(keyId, userId));

        String alias = StringUtils.hasText(event.alias()) ? event.alias().trim() : entity.getAlias();
        entity.apply(null, event.provider(), alias, mapStatus(event.status()), updatedAt);
        apiKeyMetadataRepository.save(entity);
    }

    @Transactional
    public void upsertFromTeamRegistered(TeamApiKeyRegisteredEvent event) {
        upsertTeamMetadataAllMembers(
                event.apiKeyId(),
                event.teamId(),
                event.actorUserId(),
                event.recipientUserIds(),
                event.provider(),
                event.alias(),
                ApiKeyStatus.ACTIVE,
                event.occurredAt()
        );
    }

    @Transactional
    public void upsertFromTeamUpdated(TeamApiKeyUpdatedEvent event) {
        upsertTeamMetadataAllMembers(
                event.apiKeyId(),
                event.teamId(),
                event.actorUserId(),
                event.recipientUserIds(),
                event.provider(),
                event.alias(),
                ApiKeyStatus.ACTIVE,
                event.occurredAt()
        );
    }

    @Transactional
    public void handleTeamDeleted(TeamApiKeyDeletedEvent event) {
        if (event.apiKeyId() == null || event.teamId() == null) {
            throw new IllegalArgumentException("apiKeyId and teamId are required");
        }
        String keyId = String.valueOf(event.apiKeyId());
        String teamId = String.valueOf(event.teamId());
        apiKeyMetadataRepository.deleteAllTeamMetadataRowsForKey(keyId, teamId);
    }

    @Transactional
    public void handleTeamDeletionScheduled(TeamApiKeyDeletionScheduledEvent event) {
        upsertTeamMetadataAllMembers(
                event.apiKeyId(),
                event.teamId(),
                event.actorUserId(),
                event.recipientUserIds(),
                event.provider(),
                event.alias(),
                ApiKeyStatus.DELETION_REQUESTED,
                event.occurredAt()
        );
    }

    @Transactional
    public void handleTeamDeletionCancelled(TeamApiKeyDeletionCancelledEvent event) {
        upsertTeamMetadataAllMembers(
                event.apiKeyId(),
                event.teamId(),
                event.actorUserId(),
                event.recipientUserIds(),
                event.provider(),
                event.alias(),
                ApiKeyStatus.ACTIVE,
                event.occurredAt()
        );
    }

    @Transactional
    public void upsertFromTeamStatusChanged(TeamApiKeyStatusChangedEvent event) {
        upsertTeamMetadataAllMembers(
                event.teamApiKeyId(),
                event.teamId(),
                event.ownerUserId(),
                null,
                event.provider(),
                event.alias(),
                mapTeamStatus(event.status()),
                event.occurredAt()
        );
    }

    @Transactional
    public void upsertFromUsageRecordedEvent(UsageRecordedEvent event) {
        if (event == null) {
            return;
        }
        String normalizedTeamId = UsageRecordedEventScopeNormalizer.normalizeTeamId(event.teamId());
        String normalizedTeamApiKeyId = UsageRecordedEventScopeNormalizer.normalizeTeamApiKeyId(
                event.teamApiKeyId(),
                normalizedTeamId
        );

        boolean teamMetadata = UsageRecordedMetadataScope.isTeamKeyMetadata(event);
        String metadataKeyId = resolveMetadataKeyIdFromUsage(event, teamMetadata, normalizedTeamApiKeyId);
        if (!StringUtils.hasText(metadataKeyId)) {
            return;
        }

        Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        if (teamMetadata) {
            String teamKeyForMetadata = normalizedTeamApiKeyId != null ? normalizedTeamApiKeyId.trim() : "";
            if (!StringUtils.hasText(teamKeyForMetadata)) {
                return;
            }
            String memberUserId = resolveUserId(event, teamKeyForMetadata);
            var id = ApiKeyMetadataEntityId.team(teamKeyForMetadata, memberUserId);
            ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(id)
                    .orElseGet(() -> ApiKeyMetadataEntity.createTeamRow(
                            teamKeyForMetadata,
                            memberUserId,
                            StringUtils.hasText(normalizedTeamId) ? normalizedTeamId : null
                    ));

            String alias = StringUtils.hasText(event.apiKeyAlias()) ? event.apiKeyAlias().trim() : entity.getAlias();
            String teamIdForApply = StringUtils.hasText(normalizedTeamId) ? normalizedTeamId : entity.getTeamId();
            String resolvedProvider = resolveProviderStringFromUsage(event, entity);
            entity.apply(
                    teamIdForApply,
                    resolvedProvider,
                    alias,
                    entity.getStatus() != null ? entity.getStatus() : ApiKeyStatus.ACTIVE,
                    updatedAt
            );
            apiKeyMetadataRepository.save(entity);
            return;
        }

        String ownerUserId = resolvePersonalMetadataOwnerUserId(event, metadataKeyId);
        var id = ApiKeyMetadataEntityId.personal(metadataKeyId.trim(), ownerUserId);
        ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(id)
                .orElseGet(() -> ApiKeyMetadataEntity.createPersonal(metadataKeyId.trim(), ownerUserId));

        String alias = StringUtils.hasText(event.apiKeyAlias()) ? event.apiKeyAlias().trim() : entity.getAlias();
        String resolvedProvider = resolveProviderStringFromUsage(event, entity);
        entity.apply(
                null,
                resolvedProvider,
                alias,
                entity.getStatus() != null ? entity.getStatus() : ApiKeyStatus.ACTIVE,
                updatedAt
        );
        apiKeyMetadataRepository.save(entity);
    }

    private static String resolvePersonalMetadataOwnerUserId(UsageRecordedEvent event, String metadataKeyId) {
        if (StringUtils.hasText(event.metadataOwnerUserId())) {
            return event.metadataOwnerUserId().trim();
        }
        return resolveUserId(event, metadataKeyId);
    }

    private static String resolveProviderStringFromUsage(UsageRecordedEvent event, ApiKeyMetadataEntity entity) {
        if (StringUtils.hasText(entity.getProvider())) {
            return entity.getProvider();
        }
        if (event.provider() != null) {
            return event.provider().name();
        }
        return entity.getProvider();
    }

    private static String resolveMetadataKeyIdFromUsage(
            UsageRecordedEvent event,
            boolean teamMetadata,
            String normalizedTeamApiKeyId
    ) {
        if (teamMetadata && StringUtils.hasText(normalizedTeamApiKeyId)) {
            return normalizedTeamApiKeyId.trim();
        }
        return event.apiKeyId() != null ? event.apiKeyId().trim() : "";
    }

    @Transactional
    public void handleExternalApiKeyDeleted(ExternalApiKeyDeletedEvent event) {
        if (event.apiKeyId() == null || event.userId() == null) {
            throw new IllegalArgumentException("apiKeyId, userId are required");
        }
        String keyId = String.valueOf(event.apiKeyId());
        String userId = String.valueOf(event.userId());
        boolean retainLogs = event.retainLogs() == null || event.retainLogs();

        var id = ApiKeyMetadataEntityId.personal(keyId, userId);

        if (!retainLogs) {
            int removedLogs = usageRecordedLogRepository.deleteByApiKeyId(keyId);
            apiKeyMetadataRepository.deleteById(id);
            log.info(
                    "External API key purged from usage (retainLogs=false) keyId={} userId={} removedLogs={}",
                    keyId,
                    userId,
                    removedLogs
            );
            return;
        }

        Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(id)
                .orElseGet(() -> ApiKeyMetadataEntity.createPersonal(keyId, userId));

        String alias = StringUtils.hasText(event.alias()) ? event.alias().trim() : entity.getAlias();
        String provider = StringUtils.hasText(event.provider()) ? event.provider().trim() : entity.getProvider();

        entity.apply(null, provider, alias, ApiKeyStatus.DELETED, updatedAt);
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

    private void upsertTeamMetadataAllMembers(
            Long keyIdRaw,
            Long teamIdRaw,
            String actorUserId,
            List<String> recipientsFromEvent,
            String provider,
            String alias,
            ApiKeyStatus status,
            Instant occurredAt
    ) {
        if (keyIdRaw == null || teamIdRaw == null) {
            throw new IllegalArgumentException("team api key id and team id are required");
        }
        String keyId = String.valueOf(keyIdRaw);
        String teamId = String.valueOf(teamIdRaw);
        Instant updatedAt = occurredAt != null ? occurredAt : Instant.now();

        String resolvedActor = StringUtils.hasText(actorUserId) ? actorUserId.trim() : null;
        if (!StringUtils.hasText(resolvedActor)) {
            log.warn("Skipping team API key metadata upsert due to missing actor userId keyId={}", keyId);
            return;
        }

        List<String> members = resolveTeamMemberUserIds(recipientsFromEvent, resolvedActor, teamId);
        if (members.isEmpty()) {
            log.warn("Skipping team API key metadata upsert: no members resolved keyId={} teamId={}", keyId, teamId);
            return;
        }

        String resolvedProvider = StringUtils.hasText(provider) ? provider.trim() : null;
        if (!StringUtils.hasText(resolvedProvider)) {
            String fromExisting = null;
            for (String memberId : members) {
                var existing = apiKeyMetadataRepository.findById(ApiKeyMetadataEntityId.team(keyId, memberId));
                if (existing.isPresent() && StringUtils.hasText(existing.get().getProvider())) {
                    fromExisting = existing.get().getProvider();
                    break;
                }
            }
            resolvedProvider = fromExisting;
        }
        if (!StringUtils.hasText(resolvedProvider)) {
            log.warn("Skipping team API key metadata upsert due to missing provider keyId={}", keyId);
            return;
        }

        for (String memberUserId : members) {
            var id = ApiKeyMetadataEntityId.team(keyId, memberUserId);
            ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(id)
                    .orElseGet(() -> ApiKeyMetadataEntity.createTeamRow(keyId, memberUserId, teamId));
            String resolvedAlias = StringUtils.hasText(alias) ? alias.trim() : entity.getAlias();
            entity.apply(teamId, resolvedProvider, resolvedAlias, status, updatedAt);
            apiKeyMetadataRepository.save(entity);
        }
        apiKeyMetadataRepository.deleteTeamMetadataRowsForKeyNotInMemberList(keyId, teamId, members);
    }

    private List<String> resolveTeamMemberUserIds(List<String> recipientsFromEvent, String actorUserId, String teamId) {
        if (recipientsFromEvent != null && !recipientsFromEvent.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String id : recipientsFromEvent) {
                if (StringUtils.hasText(id)) {
                    out.add(id.trim());
                }
            }
            return out;
        }
        return teamServiceClient.fetchTeamMemberUserIds(actorUserId, teamId);
    }

    private static String resolveUserId(UsageRecordedEvent event, String keyId) {
        if (!StringUtils.hasText(event.userId())) {
            throw new IllegalArgumentException("usage event userId is required for keyId=" + keyId);
        }
        return event.userId().trim();
    }
}
