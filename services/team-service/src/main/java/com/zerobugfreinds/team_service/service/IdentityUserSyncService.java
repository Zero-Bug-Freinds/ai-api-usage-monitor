package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.IdentityUserSyncEvent;
import com.zerobugfreinds.team_service.entity.IdentityUserSyncEntity;
import com.zerobugfreinds.team_service.repository.IdentityUserSyncRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class IdentityUserSyncService {

    private static final Logger log = LoggerFactory.getLogger(IdentityUserSyncService.class);

    private static final String UNKNOWN_EVENT_TYPE = "USER_SYNC_UNKNOWN";
    private static final String UNKNOWN_EMAIL_DOMAIN = "@unknown.local";

    private final ObjectMapper objectMapper;
    private final IdentityUserSyncRepository identityUserSyncRepository;
    private final IdentityUserLookupClient identityUserLookupClient;

    public IdentityUserSyncService(
            ObjectMapper objectMapper,
            IdentityUserSyncRepository identityUserSyncRepository,
            IdentityUserLookupClient identityUserLookupClient
    ) {
        this.objectMapper = objectMapper;
        this.identityUserSyncRepository = identityUserSyncRepository;
        this.identityUserLookupClient = identityUserLookupClient;
    }

    @Transactional
    public void syncUser(String payload) throws JsonProcessingException {
        IdentityUserSyncEvent event = objectMapper.readValue(payload, IdentityUserSyncEvent.class);
        String userId = normalizePrincipalUserId(normalizeRequired(event.userId(), "userId"));
        String email = normalizeEmailFromEvent(event.email(), userId);
        String displayName = normalizeDisplayName(event.name(), email);
        String eventType = normalizeEventType(event.eventType());
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        IdentityUserSyncEntity entity = identityUserSyncRepository.findById(userId)
                .orElseGet(() -> IdentityUserSyncEntity.create(userId, email, displayName, eventType, occurredAt));
        entity.apply(email, displayName, eventType, occurredAt);
        identityUserSyncRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public boolean existsUser(String userIdOrEmail) {
        if (!StringUtils.hasText(userIdOrEmail)) {
            return false;
        }
        String normalized = userIdOrEmail.trim();
        if (normalized.contains("@")) {
            String emailKey = normalized.toLowerCase(Locale.ROOT);
            return identityUserSyncRepository.existsByEmailIgnoreCase(emailKey)
                    || identityUserSyncRepository.existsById(emailKey)
                    || identityUserLookupClient.existsByEmail(emailKey);
        }
        return identityUserSyncRepository.existsById(normalized)
                || identityUserLookupClient.findExistingUserIds(List.of(normalized)).contains(normalized);
    }

    @Transactional(readOnly = true)
    public Set<String> resolveMembershipLookupCandidates(String userIdOrEmail) {
        if (!StringUtils.hasText(userIdOrEmail)) {
            return Set.of();
        }
        String normalized = userIdOrEmail.trim();
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);

        if (normalized.contains("@")) {
            String normalizedEmail = normalized.toLowerCase(Locale.ROOT);
            candidates.add(normalizedEmail);
            identityUserSyncRepository.findById(normalizedEmail)
                    .ifPresent(entity -> addSyncIdentifiers(candidates, entity));
            identityUserSyncRepository.findByEmailIgnoreCase(normalizedEmail)
                    .ifPresent(entity -> addSyncIdentifiers(candidates, entity));
        } else {
            identityUserSyncRepository.findById(normalized)
                    .map(IdentityUserSyncEntity::getEmail)
                    .filter(StringUtils::hasText)
                    .ifPresent(email -> candidates.add(email.trim().toLowerCase()));
            String emailFromIdentityService = identityUserLookupClient.findEmailByUserId(normalized);
            if (StringUtils.hasText(emailFromIdentityService)) {
                candidates.add(emailFromIdentityService.trim().toLowerCase());
            }
        }
        identityUserLookupClient.addResolvedPrincipalIdentifiers(normalized, candidates);
        log.info(
                "membershipLookupCandidates input={} candidateCount={} candidates={}",
                normalized,
                candidates.size(),
                candidates
        );
        return candidates;
    }

    private static String normalizeRequired(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException(fieldName + " 값이 비어 있습니다");
        }
        return raw.trim();
    }

    /**
     * identity MQ 의 {@code userId} 는 JWT {@code sub}(이메일) 또는 레거시 숫자 문자열 PK 일 수 있다.
     * 이메일 형태면 소문자로 통일해 동기화 테이블 PK 와 맞춘다.
     */
    private static String normalizePrincipalUserId(String raw) {
        String trimmed = raw.trim();
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }

    /**
     * 이벤트 {@code email} 이 비었을 때: {@code userId} 가 이메일(sub)이면 그 값을 이메일로 쓰고,
     * 숫자형 레거시 PK 일 때만 synthetic 이메일을 만든다.
     */
    private static String normalizeEmailFromEvent(String rawEmail, String principalUserId) {
        if (StringUtils.hasText(rawEmail)) {
            return rawEmail.trim().toLowerCase(Locale.ROOT);
        }
        if (principalUserId.contains("@")) {
            return principalUserId.toLowerCase(Locale.ROOT);
        }
        return principalUserId + UNKNOWN_EMAIL_DOMAIN;
    }

    private static void addSyncIdentifiers(Set<String> candidates, IdentityUserSyncEntity entity) {
        if (entity.getUserId() != null && StringUtils.hasText(entity.getUserId())) {
            candidates.add(entity.getUserId().trim());
        }
        if (entity.getEmail() != null && StringUtils.hasText(entity.getEmail())) {
            candidates.add(entity.getEmail().trim().toLowerCase(Locale.ROOT));
        }
    }

    private static String normalizeDisplayName(String rawName, String fallbackEmail) {
        if (!StringUtils.hasText(rawName)) {
            return fallbackEmail;
        }
        return rawName.trim();
    }

    private static String normalizeEventType(String rawEventType) {
        if (!StringUtils.hasText(rawEventType)) {
            return UNKNOWN_EVENT_TYPE;
        }
        return rawEventType.trim();
    }
}
