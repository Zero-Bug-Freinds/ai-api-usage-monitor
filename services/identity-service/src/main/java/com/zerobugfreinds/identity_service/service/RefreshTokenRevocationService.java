package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 보안 이벤트 기반 리프레시 토큰 무효화 처리.
 */
@Service
public class RefreshTokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRevocationService.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocationService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void revokeAllByUserId(Long userId, String sourceTeamId, Instant occurredAt) {
        refreshTokenRepository.deleteAllByUserId(userId);
        log.warn(
                "Revoked refresh tokens due to TEAM_MEMBER_REMOVED userId={} teamId={} occurredAt={}",
                userId,
                sourceTeamId,
                occurredAt
        );
    }
}
