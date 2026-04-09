package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.dto.TeamApiKeySummaryResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.entity.TeamEntity;
import com.zerobugfreinds.team_service.exception.ForbiddenTeamAccessException;
import com.zerobugfreinds.team_service.exception.TeamNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import com.zerobugfreinds.team_service.repository.TeamRepository;
import com.zerobugfreinds.team_service.util.EncryptionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TeamApiKeyService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamApiKeyRepository teamApiKeyRepository;
    private final EncryptionUtil encryptionUtil;

    public TeamApiKeyService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamApiKeyRepository teamApiKeyRepository,
            EncryptionUtil encryptionUtil
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.encryptionUtil = encryptionUtil;
    }

    @Transactional
    public TeamApiKeySummaryResponse register(
            String actorUserId,
            Long teamId,
            TeamApiKeyProvider provider,
            String alias,
            String externalKey
    ) {
        validateTeamAccess(actorUserId, teamId);
        if (provider == null) {
            throw new IllegalArgumentException("provider는 필수입니다");
        }

        String normalizedAlias = normalizeAlias(alias);
        String normalizedExternalKey = normalizeExternalKey(externalKey);
        String keyHash = encryptionUtil.sha256HexForUniqueness(provider.name(), normalizedExternalKey);

        if (teamApiKeyRepository.existsByTeamIdAndKeyAlias(teamId, normalizedAlias)) {
            throw new IllegalArgumentException("이미 사용 중인 API Key 별칭입니다");
        }
        if (teamApiKeyRepository.existsByTeamIdAndProviderAndKeyHash(teamId, provider, keyHash)) {
            throw new IllegalArgumentException("이미 등록된 API Key입니다");
        }

        String encrypted = encryptionUtil.encryptAes256Gcm(normalizedExternalKey);
        TeamApiKeyEntity saved = teamApiKeyRepository.save(
                TeamApiKeyEntity.register(teamId, provider, normalizedAlias, keyHash, encrypted)
        );
        return toSummary(saved);
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

    private static TeamApiKeySummaryResponse toSummary(TeamApiKeyEntity entity) {
        return new TeamApiKeySummaryResponse(
                entity.getId(),
                entity.getProvider().name(),
                entity.getKeyAlias(),
                maskedKeyPreview(entity.getProvider().name(), entity.getKeyHash()),
                entity.getCreatedAt()
        );
    }

    private static String maskedKeyPreview(String provider, String keyHash) {
        String last4 = keyHash.length() >= 4 ? keyHash.substring(keyHash.length() - 4) : keyHash;
        return provider + "-****" + last4;
    }
}
