package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.dto.InternalTeamApiKeyResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.exception.InternalRequestUnauthorizedException;
import com.zerobugfreinds.team_service.exception.TeamApiKeyNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.util.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamTrustedApiKeyCredentialServiceTest {

    @Test
    void resolveCredential_missingBearer_throwsUnauthorized() {
        TeamTrustedApiKeyCredentialService service = newService("internal-token");
        assertThatThrownBy(() -> service.resolveCredential("10", 1L, "openai", null))
                .isInstanceOf(InternalRequestUnauthorizedException.class);
    }

    @Test
    void resolveCredential_invalidBearer_throwsUnauthorized() {
        TeamTrustedApiKeyCredentialService service = newService("internal-token");
        assertThatThrownBy(() -> service.resolveCredential("10", 1L, "openai", "Bearer wrong"))
                .isInstanceOf(InternalRequestUnauthorizedException.class);
    }

    @Test
    void resolveCredential_teamMismatch_throwsNotFound() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamTrustedApiKeyCredentialService service = newService(repository, mock(EncryptionUtil.class), "internal-token");

        when(repository.findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(
                10L, 99L, TeamApiKeyProvider.OPENAI
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCredential(
                "10",
                99L,
                "openai",
                "Bearer internal-token"
        )).isInstanceOf(TeamApiKeyNotFoundException.class);
    }

    @Test
    void resolveCredential_deletionRequestedKey_throwsNotFound() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamTrustedApiKeyCredentialService service = newService(repository, mock(EncryptionUtil.class), "internal-token");

        when(repository.findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(
                10L, 1L, TeamApiKeyProvider.OPENAI
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCredential(
                "10",
                1L,
                "openai",
                "Bearer internal-token"
        )).isInstanceOf(TeamApiKeyNotFoundException.class);
    }

    @Test
    void resolveCredential_activeKey_returnsDecryptedPlainKey() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        TeamTrustedApiKeyCredentialService service = newService(repository, encryptionUtil, "internal-token");

        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                1L,
                TeamApiKeyProvider.OPENAI,
                "team-key",
                "hash",
                "f".repeat(64),
                "encrypted-value",
                BigDecimal.ONE
        );
        ReflectionTestUtils.setField(entity, "id", 10L);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());

        when(repository.findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull(
                10L, 1L, TeamApiKeyProvider.OPENAI
        )).thenReturn(Optional.of(entity));
        when(encryptionUtil.decryptAes256Gcm("encrypted-value")).thenReturn("sk-team-plain");

        InternalTeamApiKeyResponse response = service.resolveCredential(
                "10",
                1L,
                "openai",
                "Bearer internal-token"
        );

        assertThat(response.plainKey()).isEqualTo("sk-team-plain");
        assertThat(response.keyId()).isEqualTo("10");
    }

    private static TeamTrustedApiKeyCredentialService newService(String token) {
        return newService(mock(TeamApiKeyRepository.class), mock(EncryptionUtil.class), token);
    }

    private static TeamTrustedApiKeyCredentialService newService(
            TeamApiKeyRepository repository,
            EncryptionUtil encryptionUtil,
            String token
    ) {
        return new TeamTrustedApiKeyCredentialService(repository, encryptionUtil, token);
    }
}
