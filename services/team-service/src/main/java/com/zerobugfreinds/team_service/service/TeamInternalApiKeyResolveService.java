package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.dto.InternalTeamApiKeyResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.exception.ForbiddenTeamAccessException;
import com.zerobugfreinds.team_service.exception.InternalRequestUnauthorizedException;
import com.zerobugfreinds.team_service.exception.TeamApiKeyNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.repository.TeamMemberRepository;
import com.zerobugfreinds.team_service.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class TeamInternalApiKeyResolveService {
    private static final Logger log = LoggerFactory.getLogger(TeamInternalApiKeyResolveService.class);

    private final TeamMemberRepository teamMemberRepository;
    private final TeamApiKeyRepository teamApiKeyRepository;
    private final IdentityUserSyncService identityUserSyncService;
    private final EncryptionUtil encryptionUtil;
    private final String internalToken;

    public TeamInternalApiKeyResolveService(
            TeamMemberRepository teamMemberRepository,
            TeamApiKeyRepository teamApiKeyRepository,
            IdentityUserSyncService identityUserSyncService,
            EncryptionUtil encryptionUtil,
            @Value("${team.internal.api-token:${PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.identityUserSyncService = identityUserSyncService;
        this.encryptionUtil = encryptionUtil;
        this.internalToken = internalToken;
    }

    @Transactional(readOnly = true)
    public InternalTeamApiKeyResponse resolve(
            String providerRaw,
            Long teamId,
            String userId,
            String userEmail,
            String authorizationHeader
    ) {
        validateInternalToken(authorizationHeader);
        validateInputs(teamId, userId);
        TeamApiKeyProvider provider = normalizeProvider(providerRaw);

        String normalizedUserId = userId.trim();
        Set<String> candidates = new LinkedHashSet<>(identityUserSyncService.resolveMembershipLookupCandidates(normalizedUserId));
        if (StringUtils.hasText(userEmail)) {
            candidates.add(userEmail.trim().toLowerCase(Locale.ROOT));
        }
        if (candidates.isEmpty()) {
            candidates = Set.of(normalizedUserId);
        }
        boolean isTeamMember = candidates.stream()
                .anyMatch(candidate -> teamMemberRepository.existsByTeamIdAndUserId(teamId, candidate));
        if (!isTeamMember) {
            log.warn("Internal team key lookup denied teamId={} provider={} user={}",
                    teamId, provider.name(), mask(userId));
            throw new ForbiddenTeamAccessException("팀 멤버만 팀 API 키를 조회할 수 있습니다");
        }

        TeamApiKeyEntity entity = teamApiKeyRepository
                .findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(teamId, provider)
                .orElseThrow(() -> new TeamApiKeyNotFoundException("해당 팀에 활성 상태 API 키가 없습니다"));

        String plainKey = encryptionUtil.decryptAes256Gcm(entity.getEncryptedKey());
        log.info("Internal team key lookup success teamId={} provider={} user={} keyId={}",
                teamId, provider.name(), mask(userId), entity.getId());
        return new InternalTeamApiKeyResponse(plainKey, String.valueOf(entity.getId()));
    }

    private void validateInternalToken(String authorizationHeader) {
        if (!StringUtils.hasText(internalToken)) {
            throw new InternalRequestUnauthorizedException("내부 인증 토큰이 서버에 설정되지 않았습니다");
        }
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new InternalRequestUnauthorizedException("내부 인증 토큰이 필요합니다");
        }
        String bearerToken = authorizationHeader.substring("Bearer ".length()).trim();
        if (!internalToken.equals(bearerToken)) {
            throw new InternalRequestUnauthorizedException("내부 인증 토큰이 올바르지 않습니다");
        }
    }

    private static void validateInputs(Long teamId, String userId) {
        if (teamId == null || teamId <= 0) {
            throw new IllegalArgumentException("teamId는 양수여야 합니다");
        }
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId는 필수입니다");
        }
    }

    private static TeamApiKeyProvider normalizeProvider(String providerRaw) {
        if (!StringUtils.hasText(providerRaw)) {
            throw new IllegalArgumentException("provider는 필수입니다");
        }
        return switch (providerRaw.trim().toLowerCase(Locale.ROOT)) {
            case "openai" -> TeamApiKeyProvider.OPENAI;
            case "anthropic" -> TeamApiKeyProvider.ANTHROPIC;
            case "google" -> TeamApiKeyProvider.GOOGLE;
            default -> throw new IllegalArgumentException("지원하지 않는 provider입니다: " + providerRaw);
        };
    }

    private static String mask(String userId) {
        String normalized = userId == null ? "" : userId.trim();
        if (normalized.isEmpty()) {
            return "-";
        }
        int keep = Math.min(4, normalized.length());
        return normalized.substring(0, keep) + "***";
    }
}
