package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.dto.InternalFingerprintLookupResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamApiKeyFingerprintLookupServiceTest {

    private static final String FINGERPRINT =
            "a1b2c3d4e5f60718293a4b5c6d7e8f9001122334455667788990aabbccddeeff";

    @Test
    void lookup_createdByEmail_returnsLowercaseUserId() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        IdentityUserSyncService identityUserSyncService = mock(IdentityUserSyncService.class);
        TeamApiKeyFingerprintLookupService service =
                new TeamApiKeyFingerprintLookupService(repository, identityUserSyncService);

        TeamApiKeyEntity entity = entityWithRegistrant("Owner@Example.com");
        when(repository.findAllByProviderAndApiKeyFingerprint(TeamApiKeyProvider.OPENAI, FINGERPRINT))
                .thenReturn(List.of(entity));

        InternalFingerprintLookupResponse response = service.lookup("OPENAI", FINGERPRINT);

        assertThat(response.userId()).isEqualTo("owner@example.com");
        assertThat(response.teamId()).isEqualTo(42L);
    }

    @Test
    void lookup_opaqueCreatedBy_resolvesEmailFromSync() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        IdentityUserSyncService identityUserSyncService = mock(IdentityUserSyncService.class);
        TeamApiKeyFingerprintLookupService service =
                new TeamApiKeyFingerprintLookupService(repository, identityUserSyncService);

        TeamApiKeyEntity entity = entityWithRegistrant("42");
        when(repository.findAllByProviderAndApiKeyFingerprint(TeamApiKeyProvider.OPENAI, FINGERPRINT))
                .thenReturn(List.of(entity));
        when(identityUserSyncService.resolveMembershipLookupCandidates("42"))
                .thenReturn(Set.of("42", "member@test.com"));

        InternalFingerprintLookupResponse response = service.lookup("OPENAI", FINGERPRINT);

        assertThat(response.userId()).isEqualTo("member@test.com");
    }

    @Test
    void lookup_nullCreatedBy_returnsNullUserId() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        IdentityUserSyncService identityUserSyncService = mock(IdentityUserSyncService.class);
        TeamApiKeyFingerprintLookupService service =
                new TeamApiKeyFingerprintLookupService(repository, identityUserSyncService);

        TeamApiKeyEntity entity = entityWithRegistrant(null);
        when(repository.findAllByProviderAndApiKeyFingerprint(TeamApiKeyProvider.OPENAI, FINGERPRINT))
                .thenReturn(List.of(entity));

        InternalFingerprintLookupResponse response = service.lookup("OPENAI", FINGERPRINT);

        assertThat(response.userId()).isNull();
    }

    private static TeamApiKeyEntity entityWithRegistrant(String createdByUserId) {
        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                42L,
                createdByUserId,
                TeamApiKeyProvider.OPENAI,
                "team-key",
                "hash",
                FINGERPRINT,
                "encrypted",
                BigDecimal.ONE
        );
        ReflectionTestUtils.setField(entity, "id", 7L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
        return entity;
    }
}
