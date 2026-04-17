package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.team_service.dto.IdentityUserSyncEvent;
import com.zerobugfreinds.team_service.entity.IdentityUserSyncEntity;
import com.zerobugfreinds.team_service.repository.IdentityUserSyncRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class IdentityUserSyncService {

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
        String userId = normalizeRequired(event.userId(), "userId");
        String email = normalizeEmail(event.email(), userId);
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
            return identityUserSyncRepository.existsByEmailIgnoreCase(normalized)
                    || identityUserLookupClient.existsByEmail(normalized);
        }
        return identityUserSyncRepository.existsById(normalized)
                || identityUserLookupClient.findExistingUserIds(List.of(normalized)).contains(normalized);
    }

    private static String normalizeRequired(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException(fieldName + " 값이 비어 있습니다");
        }
        return raw.trim();
    }

    private static String normalizeEmail(String rawEmail, String userId) {
        if (!StringUtils.hasText(rawEmail)) {
            return userId + UNKNOWN_EMAIL_DOMAIN;
        }
        return rawEmail.trim().toLowerCase();
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
