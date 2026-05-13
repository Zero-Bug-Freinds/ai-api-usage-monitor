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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamInternalApiKeyResolveServiceTest {

    @Test
    void resolve_missingTeamId_throwsBadRequest() {
        TeamInternalApiKeyResolveService service = newService("internal-token");
        assertThatThrownBy(() -> service.resolve("google", null, "member@test.com", null, "Bearer internal-token", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId");
    }

    @Test
    void resolve_nonMember_throwsForbidden() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = newService(
                teamMemberRepository, teamApiKeyRepository, encryptionUtil, "internal-token");

        when(teamMemberRepository.existsByTeamIdAndUserId(10L, "outsider@test.com")).thenReturn(false);

        assertThatThrownBy(() -> service.resolve("openai", 10L, "outsider@test.com", null, "Bearer internal-token", null, null))
                .isInstanceOf(ForbiddenTeamAccessException.class);
    }

    @Test
    void resolve_noActiveKey_throwsNotFound() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = newService(
                teamMemberRepository, teamApiKeyRepository, encryptionUtil, "internal-token");

        when(teamMemberRepository.existsByTeamIdAndUserId(15L, "member@test.com")).thenReturn(true);
        when(teamApiKeyRepository.findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                15L, TeamApiKeyProvider.GOOGLE
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("google", 15L, "member@test.com", null, "Bearer internal-token", null, null))
                .isInstanceOf(TeamApiKeyNotFoundException.class);
    }

    @Test
    void resolve_googleProvider_returnsGoogleKey() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = newService(
                teamMemberRepository, teamApiKeyRepository, encryptionUtil, "internal-token");

        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                22L,
                TeamApiKeyProvider.GOOGLE,
                "google-key",
                "hash",
                "encrypted-value",
                BigDecimal.ONE
        );
        ReflectionTestUtils.setField(entity, "id", 777L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());

        when(teamMemberRepository.existsByTeamIdAndUserId(22L, "member@test.com")).thenReturn(true);
        when(teamApiKeyRepository.findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                22L, TeamApiKeyProvider.GOOGLE
        )).thenReturn(Optional.of(entity));
        when(encryptionUtil.decryptAes256Gcm("encrypted-value")).thenReturn("AIza-real-key");

        InternalTeamApiKeyResponse response =
                service.resolve("google", 22L, "member@test.com", null, "Bearer internal-token", null, null);

        assertThat(response.plainKey()).isEqualTo("AIza-real-key");
        assertThat(response.keyId()).isEqualTo("777");
        verify(teamApiKeyRepository).findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                22L, TeamApiKeyProvider.GOOGLE
        );
    }

    @Test
    void resolve_requiresInternalTokenWhenConfigured() {
        TeamInternalApiKeyResolveService service = newService("internal-token");
        assertThatThrownBy(() -> service.resolve("openai", 20L, "member@test.com", null, null, null, null))
                .isInstanceOf(InternalRequestUnauthorizedException.class);
    }

    @Test
    void resolve_withoutConfiguredToken_throwsForbidden() {
        TeamInternalApiKeyResolveService service = newService("");
        assertThatThrownBy(() -> service.resolve("openai", 30L, "member@test.com", null, "Bearer any", null, null))
                .isInstanceOf(InternalRequestUnauthorizedException.class)
                .hasMessageContaining("서버에 설정되지");
    }

    @Test
    void resolve_numericUserId_matchesEmailStoredMembership() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        IdentityUserSyncService identityUserSyncService = mock(IdentityUserSyncService.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        when(identityUserSyncService.resolveMembershipLookupCandidates("42")).thenReturn(Set.of("42", "member@test.com"));

        TeamInternalApiKeyResolveService service = new TeamInternalApiKeyResolveService(
                teamMemberRepository,
                teamApiKeyRepository,
                identityUserSyncService,
                encryptionUtil,
                "internal-token"
        );

        when(teamMemberRepository.existsByTeamIdAndUserId(10L, "42")).thenReturn(false);
        when(teamMemberRepository.existsByTeamIdAndUserId(10L, "member@test.com")).thenReturn(true);

        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                10L,
                TeamApiKeyProvider.OPENAI,
                "sk-test",
                "hash",
                "enc",
                BigDecimal.ONE
        );
        ReflectionTestUtils.setField(entity, "id", 99L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());

        when(teamApiKeyRepository.findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                10L, TeamApiKeyProvider.OPENAI
        )).thenReturn(Optional.of(entity));
        when(encryptionUtil.decryptAes256Gcm("enc")).thenReturn("sk-plain");

        InternalTeamApiKeyResponse response =
                service.resolve("openai", 10L, "42", null, "Bearer internal-token", null, null);

        assertThat(response.plainKey()).isEqualTo("sk-plain");
    }

    @Test
    void resolve_deletionPendingOnlyKey_throwsNotFound() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = newService(
                teamMemberRepository, teamApiKeyRepository, encryptionUtil, "internal-token");

        when(teamMemberRepository.existsByTeamIdAndUserId(45L, "member@test.com")).thenReturn(true);
        when(teamApiKeyRepository.findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                45L, TeamApiKeyProvider.OPENAI
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("openai", 45L, "member@test.com", null, "Bearer internal-token", null, null))
                .isInstanceOf(TeamApiKeyNotFoundException.class)
                .hasMessageContaining("활성 상태 API 키");
    }

    @Test
    void resolve_withApiKeyId_usesIdLookupAndSkipsLatestQuery() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = newService(
                teamMemberRepository, teamApiKeyRepository, encryptionUtil, "internal-token");

        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                22L,
                TeamApiKeyProvider.GOOGLE,
                "google-key",
                "hash",
                "encrypted-value",
                BigDecimal.ONE
        );
        ReflectionTestUtils.setField(entity, "id", 777L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());

        when(teamMemberRepository.existsByTeamIdAndUserId(22L, "member@test.com")).thenReturn(true);
        when(teamApiKeyRepository.findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(
                777L, 22L, TeamApiKeyProvider.GOOGLE
        )).thenReturn(Optional.of(entity));
        when(encryptionUtil.decryptAes256Gcm("encrypted-value")).thenReturn("AIza-real-key");

        InternalTeamApiKeyResponse response = service.resolve(
                "google", 22L, "member@test.com", null, "Bearer internal-token", "777", null);

        assertThat(response.plainKey()).isEqualTo("AIza-real-key");
        assertThat(response.keyId()).isEqualTo("777");
        verify(teamApiKeyRepository).findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(
                777L, 22L, TeamApiKeyProvider.GOOGLE);
        verify(teamApiKeyRepository, never()).findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                any(), any());
    }

    @Test
    void resolve_withAlias_usesAliasLookup() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = newService(
                teamMemberRepository, teamApiKeyRepository, encryptionUtil, "internal-token");

        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                22L,
                TeamApiKeyProvider.GOOGLE,
                "my-alias",
                "hash",
                "encrypted-value",
                BigDecimal.ONE
        );
        ReflectionTestUtils.setField(entity, "id", 888L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());

        when(teamMemberRepository.existsByTeamIdAndUserId(22L, "member@test.com")).thenReturn(true);
        when(teamApiKeyRepository.findFirstByTeamIdAndProviderAndKeyAliasAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                22L, TeamApiKeyProvider.GOOGLE, "my-alias"
        )).thenReturn(Optional.of(entity));
        when(encryptionUtil.decryptAes256Gcm("encrypted-value")).thenReturn("AIza-alias-key");

        InternalTeamApiKeyResponse response = service.resolve(
                "google", 22L, "member@test.com", null, "Bearer internal-token", null, "my-alias");

        assertThat(response.plainKey()).isEqualTo("AIza-alias-key");
        verify(teamApiKeyRepository).findFirstByTeamIdAndProviderAndKeyAliasAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                22L, TeamApiKeyProvider.GOOGLE, "my-alias");
        verify(teamApiKeyRepository, never()).findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(
                any(), any(), any());
    }

    @Test
    void resolve_invalidApiKeyId_throwsIllegalArgumentException() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = newService(
                teamMemberRepository, teamApiKeyRepository, encryptionUtil, "internal-token");

        when(teamMemberRepository.existsByTeamIdAndUserId(22L, "member@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.resolve(
                "google", 22L, "member@test.com", null, "Bearer internal-token", "not-a-number", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKeyId");
    }

    @Test
    void resolve_geminiProvider_resolvesAsGoogle() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = newService(
                teamMemberRepository, teamApiKeyRepository, encryptionUtil, "internal-token");

        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                22L,
                TeamApiKeyProvider.GOOGLE,
                "google-key",
                "hash",
                "enc",
                BigDecimal.ONE
        );
        ReflectionTestUtils.setField(entity, "id", 1L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());

        when(teamMemberRepository.existsByTeamIdAndUserId(22L, "member@test.com")).thenReturn(true);
        when(teamApiKeyRepository.findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                22L, TeamApiKeyProvider.GOOGLE
        )).thenReturn(Optional.of(entity));
        when(encryptionUtil.decryptAes256Gcm("enc")).thenReturn("plain");

        service.resolve("gemini", 22L, "member@test.com", null, "Bearer internal-token", null, null);

        verify(teamApiKeyRepository).findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                22L, TeamApiKeyProvider.GOOGLE);
    }

    private static TeamInternalApiKeyResolveService newService(String token) {
        return newService(
                mock(TeamMemberRepository.class),
                mock(TeamApiKeyRepository.class),
                mock(EncryptionUtil.class),
                token
        );
    }

    private static TeamInternalApiKeyResolveService newService(
            TeamMemberRepository teamMemberRepository,
            TeamApiKeyRepository teamApiKeyRepository,
            EncryptionUtil encryptionUtil,
            String token
    ) {
        IdentityUserSyncService identityUserSyncService = mock(IdentityUserSyncService.class);
        when(identityUserSyncService.resolveMembershipLookupCandidates(anyString()))
                .thenAnswer(invocation -> Set.of(invocation.getArgument(0, String.class).trim()));
        return new TeamInternalApiKeyResolveService(
                teamMemberRepository,
                teamApiKeyRepository,
                identityUserSyncService,
                encryptionUtil,
                token
        );
    }
}
