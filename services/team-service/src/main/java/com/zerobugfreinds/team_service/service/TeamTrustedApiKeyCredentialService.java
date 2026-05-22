package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.dto.InternalTeamApiKeyResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.exception.InternalRequestUnauthorizedException;
import com.zerobugfreinds.team_service.exception.TeamApiKeyNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Proxy ext 팀 키 relay용 trusted credential API.
 * 팀 멤버십 검사 없이 (teamId, keyId, provider) 활성 키만 복호화한다.
 */
@Service
public class TeamTrustedApiKeyCredentialService {

    private static final Logger log = LoggerFactory.getLogger(TeamTrustedApiKeyCredentialService.class);

    private final TeamApiKeyRepository teamApiKeyRepository;
    private final EncryptionUtil encryptionUtil;
    private final String internalToken;

    public TeamTrustedApiKeyCredentialService(
            TeamApiKeyRepository teamApiKeyRepository,
            EncryptionUtil encryptionUtil,
            @Value("${team.internal.api-token:${PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.encryptionUtil = encryptionUtil;
        this.internalToken = internalToken;
    }

    @Transactional(readOnly = true)
    public InternalTeamApiKeyResponse resolveCredential(
            String keyIdRaw,
            Long teamId,
            String providerRaw,
            String authorizationHeader
    ) {
        validateInternalToken(authorizationHeader);
        validateTeamId(teamId);
        TeamApiKeyProvider provider = normalizeProvider(providerRaw);
        long keyId = parseKeyId(keyIdRaw);

        TeamApiKeyEntity entity = teamApiKeyRepository
                .findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(keyId, teamId, provider)
                .orElseThrow(() -> new TeamApiKeyNotFoundException("해당 팀에 활성 상태 API 키가 없습니다"));

        String plainKey = encryptionUtil.decryptAes256Gcm(entity.getEncryptedKey());
        log.info(
                "Trusted team key credential success teamId={} provider={} keyId={}",
                teamId,
                provider.name(),
                entity.getId()
        );
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

    private static void validateTeamId(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new IllegalArgumentException("teamId는 양수여야 합니다");
        }
    }

    private static long parseKeyId(String keyIdRaw) {
        if (!StringUtils.hasText(keyIdRaw)) {
            throw new IllegalArgumentException("keyId는 필수입니다");
        }
        try {
            return Long.parseLong(keyIdRaw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("keyId는 숫자여야 합니다", ex);
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
}
