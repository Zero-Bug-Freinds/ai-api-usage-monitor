package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.dto.InternalTeamApiKeyLookupResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.event.TeamApiKeyStatus;
import com.zerobugfreinds.team_service.exception.AmbiguousTeamApiKeyHashException;
import com.zerobugfreinds.team_service.exception.InternalRequestUnauthorizedException;
import com.zerobugfreinds.team_service.exception.TeamApiKeyNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 팀 API 키 역조회(Reverse Lookup) 서비스.
 *
 * <p>Proxy 등 내부 호출자가 클라이언트가 보낸 외부 API 키의 해시값으로
 * 어느 팀/등록 멤버/키에 매핑되는지 식별할 때 사용한다. 평문 키는 응답에 포함하지 않는다.</p>
 */
@Service
public class TeamApiKeyLookupService {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyLookupService.class);

    private final TeamApiKeyRepository teamApiKeyRepository;
    private final String internalToken;

    public TeamApiKeyLookupService(
            TeamApiKeyRepository teamApiKeyRepository,
            @Value("${team.internal.api-token:${PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.internalToken = internalToken;
    }

    @Transactional(readOnly = true)
    public InternalTeamApiKeyLookupResponse lookupByHashedKey(
            String providerRaw,
            String hashedKey,
            String authorizationHeader
    ) {
        validateInternalToken(authorizationHeader);
        TeamApiKeyProvider provider = normalizeProvider(providerRaw);
        String normalizedHash = normalizeHash(hashedKey);

        List<TeamApiKeyEntity> matches = teamApiKeyRepository.findAllByProviderAndKeyHash(provider, normalizedHash);
        if (matches.isEmpty()) {
            throw new TeamApiKeyNotFoundException("해당 해시값에 매칭되는 팀 API 키를 찾을 수 없습니다");
        }
        if (matches.size() > 1) {
            log.warn(
                    "Ambiguous team api key lookup provider={} matchCount={} hashPrefix={}",
                    provider.name(),
                    matches.size(),
                    normalizedHash.length() >= 8 ? normalizedHash.substring(0, 8) : normalizedHash
            );
            throw new AmbiguousTeamApiKeyHashException(
                    "동일한 해시값에 매칭되는 팀 API 키가 2건 이상입니다"
            );
        }
        TeamApiKeyEntity entity = matches.get(0);
        TeamApiKeyStatus status = entity.isDeletionPending()
                ? TeamApiKeyStatus.DELETION_REQUESTED
                : TeamApiKeyStatus.ACTIVE;
        return new InternalTeamApiKeyLookupResponse(
                String.valueOf(entity.getId()),
                entity.getTeamId(),
                entity.getCreatedByUserId(),
                status.name(),
                entity.getKeyAlias(),
                InternalTeamApiKeyLookupResponse.SCOPE_TEAM
        );
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

    private static TeamApiKeyProvider normalizeProvider(String providerRaw) {
        if (!StringUtils.hasText(providerRaw)) {
            throw new IllegalArgumentException("provider는 필수입니다");
        }
        return switch (providerRaw.trim()) {
            case "OPENAI" -> TeamApiKeyProvider.OPENAI;
            case "ANTHROPIC" -> TeamApiKeyProvider.ANTHROPIC;
            case "GOOGLE" -> TeamApiKeyProvider.GOOGLE;
            case "META" -> TeamApiKeyProvider.META;
            case "MISTRAL" -> TeamApiKeyProvider.MISTRAL;
            case "COHERE" -> TeamApiKeyProvider.COHERE;
            case "CLAUDE" -> TeamApiKeyProvider.CLAUDE;
            case "GROK" -> TeamApiKeyProvider.GROK;
            default -> throw new IllegalArgumentException("provider는 대문자 enum 이름만 허용합니다: " + providerRaw);
        };
    }

    private static String normalizeHash(String hashedKey) {
        if (!StringUtils.hasText(hashedKey)) {
            throw new IllegalArgumentException("hashedKey는 필수입니다");
        }
        return hashedKey.trim().toLowerCase(Locale.ROOT);
    }
}
