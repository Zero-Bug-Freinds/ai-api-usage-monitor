package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.domain.TeamMemberRole;
import com.zerobugfreinds.team_service.dto.TeamApiKeySummaryResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.entity.TeamEntity;
import com.zerobugfreinds.team_service.entity.TeamMemberEntity;
import com.zerobugfreinds.team_service.exception.ForbiddenTeamAccessException;
import com.zerobugfreinds.team_service.exception.OwnerPermissionRequiredException;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamApiKeyDeletedEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamApiKeyDeletionCancelledEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamApiKeyDeletionScheduledEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamApiKeyRegisteredEvent;
import com.zerobugfreinds.team_service.event.TeamDomainOutboundEvent.TeamApiKeyUpdatedEvent;
import com.zerobugfreinds.team_service.event.TeamEventRecipients;
import com.zerobugfreinds.team_service.exception.TeamNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import com.zerobugfreinds.team_service.repository.TeamRepository;
import com.zerobugfreinds.team_service.util.EncryptionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TeamApiKeyService {
    /** 삭제 예정 등록 시 유예 기간 기본값(일). */
    public static final int DEFAULT_DELETION_GRACE_DAYS = 7;
    private static final int MIN_DELETION_GRACE_DAYS = 0;
    private static final int MAX_DELETION_GRACE_DAYS = 365;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamApiKeyRepository teamApiKeyRepository;
    private final EncryptionUtil encryptionUtil;
    private final TeamDomainEventPublisher teamDomainEventPublisher;

    public TeamApiKeyService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamApiKeyRepository teamApiKeyRepository,
            EncryptionUtil encryptionUtil,
            TeamDomainEventPublisher teamDomainEventPublisher
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.encryptionUtil = encryptionUtil;
        this.teamDomainEventPublisher = teamDomainEventPublisher;
    }

    @Transactional
    public TeamApiKeySummaryResponse register(
            String actorUserId,
            Long teamId,
            TeamApiKeyProvider provider,
            String alias,
            String externalKey,
            BigDecimal monthlyBudgetUsd
    ) {
        validateOwnerAccess(actorUserId, teamId);
        if (provider == null) {
            throw new IllegalArgumentException("provider는 필수입니다");
        }
        if (monthlyBudgetUsd == null) {
            throw new IllegalArgumentException("monthlyBudgetUsd는 필수입니다");
        }

        String normalizedAlias = normalizeAlias(alias);
        String normalizedExternalKey = normalizeExternalKey(externalKey);
        String keyHash = encryptionUtil.sha256HexForUniqueness(provider.name(), normalizedExternalKey);

        if (teamApiKeyRepository.existsByTeamIdAndKeyAlias(teamId, normalizedAlias)) {
            throw new IllegalArgumentException("이미 사용 중인 API Key 별칭입니다");
        }
        Optional<TeamApiKeyEntity> sameKeyHash =
                teamApiKeyRepository.findByTeamIdAndProviderAndKeyHash(teamId, provider, keyHash);
        if (sameKeyHash.isPresent()) {
            if (sameKeyHash.get().isDeletionPending()) {
                throw new IllegalArgumentException("삭제 예정키와 중복입니다");
            }
            throw new IllegalArgumentException("이미 등록된 API Key입니다");
        }

        String encrypted = encryptionUtil.encryptAes256Gcm(normalizedExternalKey);
        TeamApiKeyEntity saved = teamApiKeyRepository.save(
                TeamApiKeyEntity.register(teamId, provider, normalizedAlias, keyHash, encrypted, monthlyBudgetUsd)
        );
        TeamApiKeyNotifyContext ctx = teamApiKeyNotifyContext(teamId);
        Instant occurredAt = Instant.now();
        publish(TeamApiKeyRegisteredEvent.of(
                actorUserId,
                teamId,
                ctx.teamName(),
                ctx.recipientUserIds(),
                saved.getId(),
                saved.getProvider().name(),
                saved.getKeyAlias(),
                occurredAt
        ));
        return toSummary(saved);
    }

    @Transactional
    public TeamApiKeySummaryResponse update(
            String actorUserId,
            Long teamId,
            Long apiKeyId,
            TeamApiKeyProvider provider,
            String alias,
            String externalKey,
            BigDecimal monthlyBudgetUsd
    ) {
        validateTeamAccess(actorUserId, teamId);
        if (apiKeyId == null) {
            throw new IllegalArgumentException("apiKeyId는 필수입니다");
        }
        if (monthlyBudgetUsd == null) {
            throw new IllegalArgumentException("monthlyBudgetUsd는 필수입니다");
        }

        String normalizedAlias = normalizeAlias(alias);
        String normalizedExternalKey = StringUtils.hasText(externalKey) ? externalKey.trim() : "";

        TeamApiKeyEntity entity = teamApiKeyRepository.findByIdAndTeamId(apiKeyId, teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀 API 키를 찾을 수 없습니다"));
        if (entity.isDeletionPending()) {
            throw new IllegalArgumentException("삭제 예정인 키는 수정할 수 없습니다");
        }

        if (teamApiKeyRepository.existsByTeamIdAndKeyAliasAndIdNot(teamId, normalizedAlias, apiKeyId)) {
            throw new IllegalArgumentException("이미 사용 중인 API Key 별칭입니다");
        }

        if (StringUtils.hasText(normalizedExternalKey)) {
            if (provider == null) {
                throw new IllegalArgumentException("externalKey를 변경할 때 provider는 필수입니다");
            }
            if (normalizedExternalKey.length() > 4096) {
                throw new IllegalArgumentException("externalKey 길이가 너무 깁니다");
            }
            String keyHash = encryptionUtil.sha256HexForUniqueness(provider.name(), normalizedExternalKey);
            Optional<TeamApiKeyEntity> sameKeyHashElsewhere = teamApiKeyRepository
                    .findByTeamIdAndProviderAndKeyHashAndIdNot(teamId, provider, keyHash, apiKeyId);
            if (sameKeyHashElsewhere.isPresent()) {
                if (sameKeyHashElsewhere.get().isDeletionPending()) {
                    throw new IllegalArgumentException("삭제 예정키와 중복입니다");
                }
                throw new IllegalArgumentException("이미 등록된 API Key입니다");
            }
            String encrypted = encryptionUtil.encryptAes256Gcm(normalizedExternalKey);
            entity.updateCredential(provider, normalizedAlias, keyHash, encrypted, monthlyBudgetUsd);
        } else {
            entity.updateAliasAndBudget(normalizedAlias, monthlyBudgetUsd);
        }

        TeamApiKeyNotifyContext ctx = teamApiKeyNotifyContext(teamId);
        publish(TeamApiKeyUpdatedEvent.of(
                actorUserId,
                teamId,
                ctx.teamName(),
                ctx.recipientUserIds(),
                entity.getId(),
                entity.getProvider().name(),
                entity.getKeyAlias(),
                Instant.now()
        ));
        return toSummary(entity);
    }

    @Transactional
    public TeamApiKeySummaryResponse delete(
            String actorUserId,
            Long teamId,
            Long apiKeyId,
            Integer gracePeriodDays,
            boolean retainLogs
    ) {
        validateOwnerAccess(actorUserId, teamId);
        if (apiKeyId == null) {
            throw new IllegalArgumentException("apiKeyId는 필수입니다");
        }
        TeamApiKeyEntity entity = teamApiKeyRepository.findByIdAndTeamId(apiKeyId, teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀 API 키를 찾을 수 없습니다"));
        if (entity.isDeletionPending()) {
            throw new IllegalArgumentException("이미 삭제 예정인 키입니다");
        }
        int days = resolveGracePeriodDays(gracePeriodDays);
        TeamApiKeyNotifyContext ctx = teamApiKeyNotifyContext(teamId);
        Instant occurredAt = Instant.now();
        if (days == 0) {
            publish(TeamApiKeyDeletedEvent.of(
                    actorUserId,
                    teamId,
                    ctx.teamName(),
                    ctx.recipientUserIds(),
                    entity.getId(),
                    retainLogs,
                    entity.getProvider().name(),
                    entity.getKeyAlias(),
                    occurredAt
            ));
            teamApiKeyRepository.delete(entity);
            return toSummary(entity);
        }
        entity.markDeletionRequested(Instant.now(), days, retainLogs);
        teamApiKeyRepository.save(entity);
        publish(TeamApiKeyDeletionScheduledEvent.of(
                actorUserId,
                teamId,
                ctx.teamName(),
                ctx.recipientUserIds(),
                entity.getId(),
                entity.getProvider().name(),
                entity.getKeyAlias(),
                days,
                entity.getPermanentDeletionAt(),
                occurredAt
        ));
        return toSummary(entity);
    }

    @Transactional
    public int purgeExpiredDeletions() {
        Instant now = Instant.now();
        List<TeamApiKeyEntity> expired =
                teamApiKeyRepository.findAllByPermanentDeletionAtIsNotNullAndPermanentDeletionAtBefore(now);
        for (TeamApiKeyEntity entity : expired) {
            TeamApiKeyNotifyContext ctx = teamApiKeyNotifyContext(entity.getTeamId());
            publish(TeamApiKeyDeletedEvent.of(
                    "system",
                    entity.getTeamId(),
                    ctx.teamName(),
                    ctx.recipientUserIds(),
                    entity.getId(),
                    entity.isRetainUsageLogs(),
                    entity.getProvider().name(),
                    entity.getKeyAlias(),
                    now
            ));
        }
        teamApiKeyRepository.deleteAll(expired);
        return expired.size();
    }

    @Transactional
    public TeamApiKeySummaryResponse cancelDeletion(String actorUserId, Long teamId, Long apiKeyId) {
        validateTeamAccess(actorUserId, teamId);
        if (apiKeyId == null) {
            throw new IllegalArgumentException("apiKeyId는 필수입니다");
        }
        TeamApiKeyEntity entity = teamApiKeyRepository.findByIdAndTeamId(apiKeyId, teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀 API 키를 찾을 수 없습니다"));
        if (!entity.isDeletionPending()) {
            throw new IllegalArgumentException("삭제 예정 상태가 아닙니다");
        }
        entity.clearDeletionRequest();
        teamApiKeyRepository.save(entity);
        TeamApiKeyNotifyContext ctx = teamApiKeyNotifyContext(teamId);
        publish(TeamApiKeyDeletionCancelledEvent.of(
                actorUserId,
                teamId,
                ctx.teamName(),
                ctx.recipientUserIds(),
                entity.getId(),
                entity.getProvider().name(),
                entity.getKeyAlias(),
                Instant.now()
        ));
        return toSummary(entity);
    }

    @Transactional(readOnly = true)
    public List<TeamApiKeySummaryResponse> getTeamApiKeys(String actorUserId, Long teamId) {
        validateTeamAccess(actorUserId, teamId);
        return teamApiKeyRepository.findAllByTeamIdOrderByCreatedAtDesc(teamId).stream()
                .map(TeamApiKeyService::toSummary)
                .toList();
    }

    private void validateTeamAccess(String actorUserId, Long teamId) {
        if (!StringUtils.hasText(actorUserId)) {
            throw new IllegalArgumentException("userId는 필수입니다");
        }
        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));
        if (!teamMemberRepository.existsByTeamIdAndUserId(team.getId(), actorUserId)) {
            throw new ForbiddenTeamAccessException("팀 멤버만 접근할 수 있습니다");
        }
    }

    private void validateOwnerAccess(String actorUserId, Long teamId) {
        if (!StringUtils.hasText(actorUserId)) {
            throw new IllegalArgumentException("userId는 필수입니다");
        }
        teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));
        TeamMemberEntity membership = teamMemberRepository.findByTeamIdAndUserId(teamId, actorUserId)
                .orElseThrow(() -> new ForbiddenTeamAccessException("팀 멤버만 접근할 수 있습니다"));
        if (membership.getRole() != TeamMemberRole.OWNER) {
            throw new OwnerPermissionRequiredException("팀장만 API 키를 등록/삭제할 수 있습니다");
        }
    }

    private static String normalizeAlias(String alias) {
        String normalized = StringUtils.hasText(alias) ? alias.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("alias는 필수입니다");
        }
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("alias는 100자 이하여야 합니다");
        }
        return normalized;
    }

    private static String normalizeExternalKey(String externalKey) {
        String normalized = StringUtils.hasText(externalKey) ? externalKey.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("externalKey는 필수입니다");
        }
        if (normalized.length() > 4096) {
            throw new IllegalArgumentException("externalKey 길이가 너무 깁니다");
        }
        return normalized;
    }

    private static int resolveGracePeriodDays(Integer requested) {
        int days = requested != null ? requested : DEFAULT_DELETION_GRACE_DAYS;
        if (days < MIN_DELETION_GRACE_DAYS || days > MAX_DELETION_GRACE_DAYS) {
            throw new IllegalArgumentException(
                    "유예 기간은 " + MIN_DELETION_GRACE_DAYS + "일 이상 " + MAX_DELETION_GRACE_DAYS + "일 이하로 설정할 수 있습니다"
            );
        }
        return days;
    }

    private static TeamApiKeySummaryResponse toSummary(TeamApiKeyEntity entity) {
        return new TeamApiKeySummaryResponse(
                entity.getId(),
                entity.getProvider().name(),
                entity.getKeyAlias(),
                entity.getMonthlyBudgetUsd(),
                entity.getCreatedAt(),
                entity.getDeletionRequestedAt(),
                entity.getPermanentDeletionAt(),
                entity.getDeletionGraceDays()
        );
    }

    private record TeamApiKeyNotifyContext(String teamName, List<String> recipientUserIds) {
    }

    private TeamApiKeyNotifyContext teamApiKeyNotifyContext(Long teamId) {
        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException("팀을 찾을 수 없습니다"));
        return new TeamApiKeyNotifyContext(team.getName(), TeamEventRecipients.allMemberUserIds(teamMemberRepository, teamId));
    }

    private void publish(TeamDomainOutboundEvent event) {
        teamDomainEventPublisher.publish(event);
    }
}
