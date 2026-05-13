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
            String authorizationHeader,
            String apiKeyId,
            String alias
    ) {
        validateInternalToken(authorizationHeader);
        validateInputs(teamId, userId);
        TeamApiKeyProvider provider = normalizeProvider(providerRaw);

        String normalizedUserId = userId.trim();
        Set<String> candidates = new LinkedHashSet<>(
                identityUserSyncService.resolveMembershipLookupCandidates(normalizedUserId)
        );
        if (StringUtils.hasText(userEmail)) {
            candidates.add(userEmail.trim().toLowerCase(Locale.ROOT));
        }
        if (candidates.isEmpty()) {
            candidates.add(normalizedUserId);
        }
        boolean isTeamMember = candidates.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .anyMatch(candidate -> teamMemberRepository.existsByTeamIdAndUserId(teamId, candidate));
        if (!isTeamMember) {
            log.warn("Internal team key lookup denied teamId={} provider={} user={}",
                    teamId, provider.name(), mask(userId));
            throw new ForbiddenTeamAccessException("팀 멤버만 팀 API 키를 조회할 수 있습니다");
        }

        TeamApiKeyEntity entity = selectTeamInternalKey(teamId, provider, apiKeyId, alias);

        String plainKey = encryptionUtil.decryptAes256Gcm(entity.getEncryptedKey());
        log.info("Internal team key lookup success teamId={} provider={} user={} keyId={}",
                teamId, provider.name(), mask(userId), entity.getId());
        return new InternalTeamApiKeyResponse(plainKey, String.valueOf(entity.getId()));
    }

    /**
     * 팀 범위의 활성 API 키만 조회한다. 사용자(개인) 키로의 폴백은 하지 않는다.
     *
     * <p>우선순위: {@code apiKeyId}가 있으면 ID 매칭, 없으면 {@code alias} 최신 건, 둘 다 없으면 해당
     * provider의 최신 활성 키.</p>
     */
    private TeamApiKeyEntity selectTeamInternalKey(
            Long teamId,
            TeamApiKeyProvider provider,
            String apiKeyId,
            String alias
    ) {
        if (StringUtils.hasText(apiKeyId)) {
            long keyId;
            try {
                keyId = Long.parseLong(apiKeyId.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("apiKeyId는 숫자여야 합니다", ex);
            }
            return teamApiKeyRepository
                    .findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(keyId, teamId, provider)
                    .orElseThrow(() -> new TeamApiKeyNotFoundException("해당 팀에 활성 상태 API 키가 없습니다"));
        }
        if (StringUtils.hasText(alias)) {
            return teamApiKeyRepository
                    .findFirstByTeamIdAndProviderAndKeyAliasAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                            teamId,
                            provider,
                            alias.trim()
                    )
                    .orElseThrow(() -> new TeamApiKeyNotFoundException("해당 팀에 활성 상태 API 키가 없습니다"));
        }
        return teamApiKeyRepository
                .findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(teamId, provider)
                .orElseThrow(() -> new TeamApiKeyNotFoundException("해당 팀에 활성 상태 API 키가 없습니다"));
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
            case "google", "gemini" -> TeamApiKeyProvider.GOOGLE;
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
