package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyException;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalApiKeyGlobalUniquenessTest {

	private static final String KEY_HASH = "hash-anthropic-1";
	private static final String FINGERPRINT = "fingerprint-anthropic-1";

	@Mock
	private ExternalApiKeyRepository externalApiKeyRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private EncryptionUtil encryptionUtil;
	@Mock
	private ApplicationEventPublisher applicationEventPublisher;
	@Mock
	private TeamApiKeyLookupClient teamApiKeyLookupClient;
	@Mock
	private ApiKeyFingerprintRegistrationLock apiKeyFingerprintRegistrationLock;

	@InjectMocks
	private ExternalApiKeyService externalApiKeyService;

	@BeforeEach
	void setUp() {
		lenient()
				.when(apiKeyFingerprintRegistrationLock.runWithLock(anyString(), any(Supplier.class)))
				.thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
		lenient()
				.when(encryptionUtil.sha256HexForUniqueness(anyString(), anyString()))
				.thenReturn(KEY_HASH);
		lenient()
				.when(encryptionUtil.sha256HexUtf8(anyString()))
				.thenReturn(FINGERPRINT);
		lenient()
				.when(encryptionUtil.encryptAes256Gcm(anyString()))
				.thenReturn("encrypted");
	}

	@Test
	void register_rejectsWhenFingerprintAlreadyRegisteredByAnotherUser() {
		when(teamApiKeyLookupClient.existsByRawKeyFingerprint(anyString(), eq(FINGERPRINT))).thenReturn(false);
		when(externalApiKeyRepository.findAllByApiKeyFingerprint(FINGERPRINT))
				.thenReturn(List.of(ownedBy(10L, 99L)));
		when(externalApiKeyRepository.findByUserIdAndProviderAndKeyHash(7L, ExternalApiKeyProvider.ANTHROPIC, KEY_HASH))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> externalApiKeyService.register(
						7L,
						ExternalApiKeyProvider.ANTHROPIC,
						"my-key",
						"sk-ant-other",
						BigDecimal.TEN))
				.isInstanceOf(DuplicateExternalApiKeyException.class)
				.hasMessage("이미 등록된 API 키입니다");
	}

	private static ExternalApiKeyEntity ownedBy(long keyId, long userId) {
		ExternalApiKeyEntity entity = ExternalApiKeyEntity.register(
				userId,
				ExternalApiKeyProvider.ANTHROPIC,
				"existing",
				KEY_HASH,
				FINGERPRINT,
				"cipher",
				BigDecimal.ONE
		);
		ReflectionTestUtils.setField(entity, "id", keyId);
		return entity;
	}
}
