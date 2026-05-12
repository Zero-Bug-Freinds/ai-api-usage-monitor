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
        String metadataKeyId = resolveMetadataKeyIdFromUsage(event);
        if (!StringUtils.hasText(metadataKeyId)) {
            return;
        }

        Instant updatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        if (StringUtils.hasText(event.teamApiKeyId())) {
            String teamId = event.teamId() != null ? event.teamId().trim() : "";
            String memberUserId = resolveUserId(event, metadataKeyId);
            var id = ApiKeyMetadataEntityId.team(metadataKeyId.trim(), memberUserId);
            ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(id)
                    .orElseGet(() -> ApiKeyMetadataEntity.createTeamRow(metadataKeyId.trim(), memberUserId, teamId));

            String alias = StringUtils.hasText(event.apiKeyAlias()) ? event.apiKeyAlias().trim() : entity.getAlias();
            entity.apply(
                    StringUtils.hasText(teamId) ? teamId : entity.getTeamId(),
                    entity.getProvider(),
                    alias,
                    entity.getStatus() != null ? entity.getStatus() : ApiKeyStatus.ACTIVE,
                    updatedAt
            );
            apiKeyMetadataRepository.save(entity);
            return;
        }

        String userId = resolveUserId(event, metadataKeyId);
        var id = ApiKeyMetadataEntityId.personal(metadataKeyId.trim(), userId);
        ApiKeyMetadataEntity entity = apiKeyMetadataRepository.findById(id)
                .orElseGet(() -> ApiKeyMetadataEntity.createPersonal(metadataKeyId.trim(), userId));

        String alias = StringUtils.hasText(event.apiKeyAlias()) ? event.apiKeyAlias().trim() : entity.getAlias();
        entity.apply(
                null,
                entity.getProvider(),
                alias,
                entity.getStatus() != null ? entity.getStatus() : ApiKeyStatus.ACTIVE,
                updatedAt
        );
        apiKeyMetadataRepository.save(entity);
    }

    /**
     * Team traffic: prefer {@link UsageRecordedEvent#teamApiKeyId()} as metadata PK (team key row id).
     * Personal traffic: {@link UsageRecordedEvent#apiKeyId()}.
     */
    private static String resolveMetadataKeyIdFromUsage(UsageRecordedEvent event) {
        if (StringUtils.hasText(event.teamApiKeyId())) {
            return event.teamApiKeyId().trim();
        }
        return event.apiKeyId() != null ? event.apiKeyId().trim() : "";
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

        List<String> members = new ArrayList<>(teamServiceClient.fetchTeamMemberUserIds(resolvedActor, teamId));
        if (members.isEmpty()) {
            log.warn("Team member list empty; falling back to actor only keyId={} teamId={}", keyId, teamId);
            members.add(resolvedActor);
        }

        String resolvedProvider = StringUtils.hasText(provider) ? provider.trim() : null;
        if (!StringUtils.hasText(resolvedProvider)) {
            String fromExisting = null;
            for (String memberId : members) {
                var probeId = ApiKeyMetadataEntityId.team(keyId, memberId);
                var existing = apiKeyMetadataRepository.findById(probeId);
                if (existing.isPresent() && StringUtils.hasText(existing.get().getProvider())) {
                    fromExisting = existing.get().getProvider().trim();
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
            ApiKeyMetadataEntity target = apiKeyMetadataRepository.findById(id)
                    .orElseGet(() -> ApiKeyMetadataEntity.createTeamRow(keyId, memberUserId, teamId));
            String resolvedAlias = StringUtils.hasText(alias) ? alias.trim() : target.getAlias();
            target.apply(teamId, resolvedProvider, resolvedAlias, status, updatedAt);
            apiKeyMetadataRepository.save(target);
        }

        int removed = apiKeyMetadataRepository.deleteTeamMetadataRowsForKeyNotInMemberList(keyId, teamId, members);
        if (removed > 0) {
            log.debug("Removed {} stale team api_key_metadata rows for keyId={} teamId={}", removed, keyId, teamId);
        }
    }

    private static String resolveUserId(UsageRecordedEvent event, String keyId) {
        if (!StringUtils.hasText(event.userId())) {
            throw new IllegalArgumentException("usage event userId is required for keyId=" + keyId);
        }
        return event.userId().trim();
    }
}
