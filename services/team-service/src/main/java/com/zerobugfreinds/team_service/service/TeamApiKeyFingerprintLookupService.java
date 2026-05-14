package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.dto.InternalFingerprintLookupResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.event.TeamApiKeyStatus;
import com.zerobugfreinds.team_service.exception.AmbiguousTeamApiKeyHashException;
import com.zerobugfreinds.team_service.exception.TeamApiKeyNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * SHA-256(평문 키) fingerprint 기반 팀 API 키 역조회.
 */
@Service
public class TeamApiKeyFingerprintLookupService {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyFingerprintLookupService.class);

    private final TeamApiKeyRepository teamApiKeyRepository;

    public TeamApiKeyFingerprintLookupService(TeamApiKeyRepository teamApiKeyRepository) {
        this.teamApiKeyRepository = teamApiKeyRepository;
    }

    @Transactional(readOnly = true)
    public InternalFingerprintLookupResponse lookup(String providerRaw, String fingerprint) {
        TeamApiKeyProvider provider = normalizeProvider(providerRaw);
        String normalizedFingerprint = normalizeFingerprintHex(fingerprint);

        List<TeamApiKeyEntity> matches = teamApiKeyRepository.findAllByProviderAndApiKeyFingerprint(
                provider,
                normalizedFingerprint
        );
        if (matches.isEmpty()) {
            throw new TeamApiKeyNotFoundException("해당 fingerprint 에 매칭되는 팀 API 키를 찾을 수 없습니다");
        }
        if (matches.size() > 1) {
            log.warn(
                    "Ambiguous team api key fingerprint lookup provider={} matchCount={} fingerprintPrefix={}",
                    provider.name(),
                    matches.size(),
                    normalizedFingerprint.length() >= 8 ? normalizedFingerprint.substring(0, 8) : normalizedFingerprint
            );
            throw new AmbiguousTeamApiKeyHashException(
                    "동일한 fingerprint 에 매칭되는 팀 API 키가 2건 이상입니다"
            );
        }
        TeamApiKeyEntity entity = matches.get(0);
        TeamApiKeyStatus status = entity.isDeletionPending()
                ? TeamApiKeyStatus.DELETION_REQUESTED
                : TeamApiKeyStatus.ACTIVE;
        return InternalFingerprintLookupResponse.team(
                entity.getTeamId(),
                String.valueOf(entity.getId()),
                entity.getKeyAlias(),
                status.name(),
                "team"
        );
    }

    private static TeamApiKeyProvider normalizeProvider(String providerRaw) {
        if (!StringUtils.hasText(providerRaw)) {
            throw new IllegalArgumentException("provider는 필수입니다");
        }
        return switch (providerRaw.trim()) {
            case "OPENAI" -> TeamApiKeyProvider.OPENAI;
            case "ANTHROPIC" -> TeamApiKeyProvider.ANTHROPIC;
            case "GOOGLE", "GEMINI" -> TeamApiKeyProvider.GOOGLE;
            case "META" -> TeamApiKeyProvider.META;
            case "MISTRAL" -> TeamApiKeyProvider.MISTRAL;
            case "COHERE" -> TeamApiKeyProvider.COHERE;
            case "CLAUDE" -> TeamApiKeyProvider.CLAUDE;
            case "GROK" -> TeamApiKeyProvider.GROK;
            default -> throw new IllegalArgumentException("provider는 대문자 enum 이름만 허용합니다: " + providerRaw);
        };
    }

    private static String normalizeFingerprintHex(String fingerprint) {
        if (!StringUtils.hasText(fingerprint)) {
            throw new IllegalArgumentException("fingerprint는 필수입니다");
        }
        String trimmed = fingerprint.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint 는 64자리 소문자 hex 여야 합니다");
        }
        return trimmed;
    }
}
