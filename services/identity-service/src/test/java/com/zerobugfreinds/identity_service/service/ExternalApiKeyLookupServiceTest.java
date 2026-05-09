package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.dto.InternalApiKeyLookupResponse;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import com.zerobugfreinds.identity_service.exception.AmbiguousExternalApiKeyHashException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyNotFoundException;
import com.zerobugfreinds.identity_service.repository.ExternalApiKeyRepository;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import com.zerobugfreinds.identity_service.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalApiKeyLookupServiceTest {

	private static final String SAMPLE_HASH =
			"a1b2c3d4e5f60718293a4b5c6d7e8f9001122334455667788990aabbccddeeff";

	@Mock
	private ExternalApiKeyRepository externalApiKeyRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private EncryptionUtil encryptionUtil;
	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@InjectMocks
	private ExternalApiKeyService externalApiKeyService;

	@BeforeEach
	void setUp() {
		externalApiKeyService = new ExternalApiKeyService(
				externalApiKeyRepository,
				userRepository,
				encryptionUtil,
				applicationEventPublisher
		);
	}

	@Test
	void lookupByHashedKey_singleActiveMatch_returnsActiveResponse() {
		ExternalApiKeyEntity entity = newEntity(11L, 7L, "openai-default", false);
		when(externalApiKeyRepository.findAllByProviderAndKeyHash(
				eq(ExternalApiKeyProvider.OPENAI), eq(SAMPLE_HASH)
		)).thenReturn(List.of(entity));

		InternalApiKeyLookupResponse response =
				externalApiKeyService.lookupByHashedKey(ExternalApiKeyProvider.OPENAI, SAMPLE_HASH);

		assertThat(response.keyId()).isEqualTo("11");
		assertThat(response.ownerId()).isEqualTo(7L);
		assertThat(response.status()).isEqualTo("ACTIVE");
		assertThat(response.alias()).isEqualTo("openai-default");
		assertThat(response.scope()).isEqualTo(InternalApiKeyLookupResponse.SCOPE_USER);
	}

	@Test
	void lookupByHashedKey_singleDeletionPendingMatch_returnsDeletionRequestedResponse() {
		ExternalApiKeyEntity entity = newEntity(22L, 9L, "anthropic-old", true);
		when(externalApiKeyRepository.findAllByProviderAndKeyHash(
				eq(ExternalApiKeyProvider.ANTHROPIC), eq(SAMPLE_HASH)
		)).thenReturn(List.of(entity));

		InternalApiKeyLookupResponse response =
				externalApiKeyService.lookupByHashedKey(ExternalApiKeyProvider.ANTHROPIC, SAMPLE_HASH);

		assertThat(response.keyId()).isEqualTo("22");
		assertThat(response.status()).isEqualTo("DELETION_REQUESTED");
	}

	@Test
	void lookupByHashedKey_normalizesUppercaseHashBeforeQuery() {
		ExternalApiKeyEntity entity = newEntity(33L, 5L, "google-default", false);
		when(externalApiKeyRepository.findAllByProviderAndKeyHash(
				eq(ExternalApiKeyProvider.GOOGLE), eq(SAMPLE_HASH)
		)).thenReturn(List.of(entity));

		InternalApiKeyLookupResponse response =
				externalApiKeyService.lookupByHashedKey(ExternalApiKeyProvider.GOOGLE, "  " + SAMPLE_HASH.toUpperCase() + "  ");

		assertThat(response.keyId()).isEqualTo("33");
	}

	@Test
	void lookupByHashedKey_noMatch_throwsNotFound() {
		when(externalApiKeyRepository.findAllByProviderAndKeyHash(
				eq(ExternalApiKeyProvider.OPENAI), eq(SAMPLE_HASH)
		)).thenReturn(List.of());

		assertThatThrownBy(() ->
				externalApiKeyService.lookupByHashedKey(ExternalApiKeyProvider.OPENAI, SAMPLE_HASH)
		).isInstanceOf(ExternalApiKeyNotFoundException.class);
	}

	@Test
	void lookupByHashedKey_multipleMatches_throwsConflict() {
		ExternalApiKeyEntity first = newEntity(101L, 1L, "shared-key-1", false);
		ExternalApiKeyEntity second = newEntity(102L, 2L, "shared-key-2", false);
		when(externalApiKeyRepository.findAllByProviderAndKeyHash(
				eq(ExternalApiKeyProvider.OPENAI), eq(SAMPLE_HASH)
		)).thenReturn(List.of(first, second));

		assertThatThrownBy(() ->
				externalApiKeyService.lookupByHashedKey(ExternalApiKeyProvider.OPENAI, SAMPLE_HASH)
		).isInstanceOf(AmbiguousExternalApiKeyHashException.class);
	}

	@Test
	void lookupByHashedKey_blankHash_throwsBadRequest() {
		assertThatThrownBy(() ->
				externalApiKeyService.lookupByHashedKey(ExternalApiKeyProvider.OPENAI, "  ")
		).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("hashedKey");
	}

	@Test
	void lookupByHashedKey_nullProvider_throwsBadRequest() {
		assertThatThrownBy(() ->
				externalApiKeyService.lookupByHashedKey(null, SAMPLE_HASH)
		).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("provider");
	}

	private static ExternalApiKeyEntity newEntity(Long id, Long userId, String alias, boolean pendingDeletion) {
		ExternalApiKeyEntity entity = ExternalApiKeyEntity.register(
				userId,
				ExternalApiKeyProvider.OPENAI,
				alias,
				SAMPLE_HASH,
				"encrypted",
				BigDecimal.ONE
		);
		ReflectionTestUtils.setField(entity, "id", id);
		if (pendingDeletion) {
			entity.markPendingDeletion(Instant.now(), 7, true);
		}
		return entity;
	}
}
