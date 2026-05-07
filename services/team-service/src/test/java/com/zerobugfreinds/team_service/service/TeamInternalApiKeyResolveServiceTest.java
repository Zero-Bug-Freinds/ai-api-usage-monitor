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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamInternalApiKeyResolveServiceTest {

    @Test
    void resolve_missingTeamId_throwsBadRequest() {
        TeamInternalApiKeyResolveService service = newService("internal-token");
        assertThatThrownBy(() -> service.resolve("google", null, "member@test.com", "Bearer internal-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId");
    }

    @Test
    void resolve_nonMember_throwsForbidden() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = new TeamInternalApiKeyResolveService(
                teamMemberRepository,
                teamApiKeyRepository,
                encryptionUtil,
                "internal-token"
        );

        when(teamMemberRepository.existsByTeamIdAndUserId(10L, "outsider@test.com")).thenReturn(false);

        assertThatThrownBy(() -> service.resolve("openai", 10L, "outsider@test.com", "Bearer internal-token"))
                .isInstanceOf(ForbiddenTeamAccessException.class);
    }

    @Test
    void resolve_noActiveKey_throwsNotFound() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = new TeamInternalApiKeyResolveService(
                teamMemberRepository,
                teamApiKeyRepository,
                encryptionUtil,
                "internal-token"
        );

        when(teamMemberRepository.existsByTeamIdAndUserId(15L, "member@test.com")).thenReturn(true);
        when(teamApiKeyRepository.findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                15L, TeamApiKeyProvider.GOOGLE
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("gemini", 15L, "member@test.com", "Bearer internal-token"))
                .isInstanceOf(TeamApiKeyNotFoundException.class);
    }

    @Test
    void resolve_geminiAlias_returnsGoogleKey() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = new TeamInternalApiKeyResolveService(
                teamMemberRepository,
                teamApiKeyRepository,
                encryptionUtil,
                "internal-token"
        );

        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                22L,
                TeamApiKeyProvider.GOOGLE,
                "gemini-key",
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
                service.resolve("gemini", 22L, "member@test.com", "Bearer internal-token");

        assertThat(response.plainKey()).isEqualTo("AIza-real-key");
        assertThat(response.keyId()).isEqualTo("777");
        verify(teamApiKeyRepository).findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                22L, TeamApiKeyProvider.GOOGLE
        );
    }

    @Test
    void resolve_requiresInternalTokenWhenConfigured() {
        TeamInternalApiKeyResolveService service = newService("internal-token");
        assertThatThrownBy(() -> service.resolve("openai", 20L, "member@test.com", null))
                .isInstanceOf(InternalRequestUnauthorizedException.class);
    }

    @Test
    void resolve_withoutConfiguredToken_throwsForbidden() {
        TeamInternalApiKeyResolveService service = newService("");
        assertThatThrownBy(() -> service.resolve("openai", 30L, "member@test.com", "Bearer any"))
                .isInstanceOf(InternalRequestUnauthorizedException.class)
                .hasMessageContaining("서버에 설정되지");
    }

    @Test
    void resolve_deletionPendingOnlyKey_throwsNotFound() {
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamApiKeyRepository teamApiKeyRepository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamInternalApiKeyResolveService service = new TeamInternalApiKeyResolveService(
                teamMemberRepository,
                teamApiKeyRepository,
                encryptionUtil,
                "internal-token"
        );

        when(teamMemberRepository.existsByTeamIdAndUserId(45L, "member@test.com")).thenReturn(true);
        when(teamApiKeyRepository.findFirstByTeamIdAndProviderAndDeletionRequestedAtIsNullOrderByCreatedAtDesc(
                45L, TeamApiKeyProvider.OPENAI
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("openai", 45L, "member@test.com", "Bearer internal-token"))
                .isInstanceOf(TeamApiKeyNotFoundException.class)
                .hasMessageContaining("활성 상태 API 키");
    }

    private static TeamInternalApiKeyResolveService newService(String token) {
        return new TeamInternalApiKeyResolveService(
                mock(TeamMemberRepository.class),
                mock(TeamApiKeyRepository.class),
                mock(EncryptionUtil.class),
                token
        );
    }
}
