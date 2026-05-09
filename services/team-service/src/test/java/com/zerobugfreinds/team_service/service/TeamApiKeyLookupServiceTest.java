package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.domain.TeamApiKeyProvider;
import com.zerobugfreinds.team_service.dto.InternalTeamApiKeyLookupResponse;
import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.exception.AmbiguousTeamApiKeyHashException;
import com.zerobugfreinds.team_service.exception.InternalRequestUnauthorizedException;
import com.zerobugfreinds.team_service.exception.TeamApiKeyNotFoundException;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamApiKeyLookupServiceTest {

    private static final String INTERNAL_TOKEN = "internal-token";
    private static final String SAMPLE_HASH =
            "a1b2c3d4e5f60718293a4b5c6d7e8f9001122334455667788990aabbccddeeff";

    @Test
    void lookupByHashedKey_singleActiveMatch_returnsActiveResponse() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        TeamApiKeyEntity entity = newEntity(101L, 42L, "openai-team", "user-1", false);
        when(repository.findAllByProviderAndKeyHash(eq(TeamApiKeyProvider.OPENAI), eq(SAMPLE_HASH)))
                .thenReturn(List.of(entity));

        InternalTeamApiKeyLookupResponse response =
                service.lookupByHashedKey("OPENAI", SAMPLE_HASH, "Bearer " + INTERNAL_TOKEN);

        assertThat(response.keyId()).isEqualTo("101");
        assertThat(response.teamId()).isEqualTo(42L);
        assertThat(response.ownerUserId()).isEqualTo("user-1");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.alias()).isEqualTo("openai-team");
        assertThat(response.scope()).isEqualTo(InternalTeamApiKeyLookupResponse.SCOPE_TEAM);
    }

    @Test
    void lookupByHashedKey_singleDeletionPendingMatch_returnsDeletionRequestedResponse() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        TeamApiKeyEntity entity = newEntity(202L, 7L, "anthropic-team", "user-2", true);
        when(repository.findAllByProviderAndKeyHash(eq(TeamApiKeyProvider.ANTHROPIC), eq(SAMPLE_HASH)))
                .thenReturn(List.of(entity));

        InternalTeamApiKeyLookupResponse response =
                service.lookupByHashedKey("ANTHROPIC", SAMPLE_HASH, "Bearer " + INTERNAL_TOKEN);

        assertThat(response.status()).isEqualTo("DELETION_REQUESTED");
    }

    @Test
    void lookupByHashedKey_googleProvider_returnsGoogleResponse() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        TeamApiKeyEntity entity = newEntity(303L, 9L, "google-team", null, false);
        when(repository.findAllByProviderAndKeyHash(eq(TeamApiKeyProvider.GOOGLE), eq(SAMPLE_HASH)))
                .thenReturn(List.of(entity));

        InternalTeamApiKeyLookupResponse response =
                service.lookupByHashedKey("GOOGLE", SAMPLE_HASH.toUpperCase(), "Bearer " + INTERNAL_TOKEN);

        assertThat(response.keyId()).isEqualTo("303");
        assertThat(response.ownerUserId()).isNull();
    }

    @Test
    void lookupByHashedKey_noMatch_throwsNotFound() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        when(repository.findAllByProviderAndKeyHash(eq(TeamApiKeyProvider.OPENAI), eq(SAMPLE_HASH)))
                .thenReturn(List.of());

        assertThatThrownBy(() ->
                service.lookupByHashedKey("OPENAI", SAMPLE_HASH, "Bearer " + INTERNAL_TOKEN)
        ).isInstanceOf(TeamApiKeyNotFoundException.class);
    }

    @Test
    void lookupByHashedKey_multipleMatches_throwsConflict() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        TeamApiKeyEntity first = newEntity(401L, 1L, "shared-1", "user-a", false);
        TeamApiKeyEntity second = newEntity(402L, 2L, "shared-2", "user-b", false);
        when(repository.findAllByProviderAndKeyHash(eq(TeamApiKeyProvider.OPENAI), eq(SAMPLE_HASH)))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() ->
                service.lookupByHashedKey("OPENAI", SAMPLE_HASH, "Bearer " + INTERNAL_TOKEN)
        ).isInstanceOf(AmbiguousTeamApiKeyHashException.class);
    }

    @Test
    void lookupByHashedKey_blankHash_throwsBadRequest() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        assertThatThrownBy(() ->
                service.lookupByHashedKey("OPENAI", "  ", "Bearer " + INTERNAL_TOKEN)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hashedKey");
    }

    @Test
    void lookupByHashedKey_unknownProvider_throwsBadRequest() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        assertThatThrownBy(() ->
                service.lookupByHashedKey("unknown-provider", SAMPLE_HASH, "Bearer " + INTERNAL_TOKEN)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void lookupByHashedKey_lowercaseProvider_throwsBadRequest() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        assertThatThrownBy(() ->
                service.lookupByHashedKey("openai", SAMPLE_HASH, "Bearer " + INTERNAL_TOKEN)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("대문자");
    }

    @Test
    void lookupByHashedKey_missingAuthHeader_throwsUnauthorized() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        assertThatThrownBy(() ->
                service.lookupByHashedKey("openai", SAMPLE_HASH, null)
        ).isInstanceOf(InternalRequestUnauthorizedException.class);
    }

    @Test
    void lookupByHashedKey_wrongBearerToken_throwsUnauthorized() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, INTERNAL_TOKEN);

        assertThatThrownBy(() ->
                service.lookupByHashedKey("openai", SAMPLE_HASH, "Bearer wrong-token")
        ).isInstanceOf(InternalRequestUnauthorizedException.class);
    }

    @Test
    void lookupByHashedKey_serverHasNoConfiguredToken_throwsUnauthorized() {
        TeamApiKeyRepository repository = mock(TeamApiKeyRepository.class);
        TeamApiKeyLookupService service = new TeamApiKeyLookupService(repository, "");

        assertThatThrownBy(() ->
                service.lookupByHashedKey("openai", SAMPLE_HASH, "Bearer any")
        ).isInstanceOf(InternalRequestUnauthorizedException.class)
                .hasMessageContaining("서버에 설정되지");
    }

    private static TeamApiKeyEntity newEntity(
            Long id,
            Long teamId,
            String alias,
            String createdByUserId,
            boolean pendingDeletion
    ) {
        TeamApiKeyEntity entity = TeamApiKeyEntity.register(
                teamId,
                createdByUserId,
                TeamApiKeyProvider.OPENAI,
                alias,
                SAMPLE_HASH,
                "encrypted",
                BigDecimal.ONE
        );
        ReflectionTestUtils.setField(entity, "id", id);
        if (pendingDeletion) {
            entity.markDeletionRequested(Instant.now(), 7, true);
        }
        return entity;
    }
}
