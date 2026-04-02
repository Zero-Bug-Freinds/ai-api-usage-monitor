package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.domain.ExternalApiKeyProvider;
import com.zerobugfreinds.identity_service.dto.InternalApiKeyResponse;
import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import com.zerobugfreinds.identity_service.exception.ApiKeyLimitExceededException;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyAliasException;
import com.zerobugfreinds.identity_service.exception.DuplicateExternalApiKeyException;
import com.zerobugfreinds.identity_service.exception.ExternalApiKeyNotFoundException;
import com.zerobugfreinds.identity_service.repository.ExternalApiKeyRepository;
import com.zerobugfreinds.identity_service.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 외부 AI API 키 등록.
 */
@Service
public class ExternalApiKeyService {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyService.class);

	private static final int MAX_KEYS_PER_USER = 5;

	private final ExternalApiKeyRepository externalApiKeyRepository;
	private final EncryptionUtil encryptionUtil;

	public ExternalApiKeyService(ExternalApiKeyRepository externalApiKeyRepository, EncryptionUtil encryptionUtil) {
		this.externalApiKeyRepository = externalApiKeyRepository;
		this.encryptionUtil = encryptionUtil;
	}

	@Transactional
	public ExternalApiKeyEntity register(Long userId, ExternalApiKeyProvider provider, String alias, String plainKey) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다");
		}
		String trimmedAlias = StringUtils.hasText(alias) ? alias.trim() : "";
		if (!StringUtils.hasText(trimmedAlias)) {
			throw new IllegalArgumentException("alias는 필수입니다");
		}
		String normalizedKey = StringUtils.hasText(plainKey) ? plainKey.trim() : "";
		if (!StringUtils.hasText(normalizedKey)) {
			throw new IllegalArgumentException("externalKey는 필수입니다");
		}

		long count = externalApiKeyRepository.countByUserId(userId);
		if (count >= MAX_KEYS_PER_USER) {
			throw new ApiKeyLimitExceededException(
					"외부 API 키는 사용자당 최대 " + MAX_KEYS_PER_USER + "개까지 등록할 수 있습니다"
			);
		}

		if (externalApiKeyRepository.existsByUserIdAndKeyAlias(userId, trimmedAlias)) {
			throw new DuplicateExternalApiKeyAliasException("이미 사용 중인 별칭입니다");
		}

		String keyHash = encryptionUtil.sha256HexForUniqueness(provider.name(), normalizedKey);
		long duplicateCount = externalApiKeyRepository.countByUserIdAndProviderAndKeyHash(userId, provider, keyHash);
		if (duplicateCount > 0) {
			log.warn(
					"[AUDIT] external_api_key_duplicate_detected userId={} provider={} alias={} hashPrefix={} duplicateCount={}",
					userId,
					provider.name(),
					trimmedAlias,
					keyHash.substring(0, 8),
					duplicateCount
			);
			throw new DuplicateExternalApiKeyException("이미 등록된 API 키입니다");
		}

		String encrypted = encryptionUtil.encryptAes256Gcm(normalizedKey);
		ExternalApiKeyEntity entity = ExternalApiKeyEntity.register(
				userId,
				provider,
				trimmedAlias,
				keyHash,
				encrypted
		);
		ExternalApiKeyEntity saved = externalApiKeyRepository.save(entity);

		log.info(
				"[AUDIT] external_api_key_registered userId={} provider={} alias={} keyId={}",
				userId,
				provider.name(),
				trimmedAlias,
				saved.getId()
		);

		return saved;
	}

	@Transactional(readOnly = true)
	public java.util.List<ExternalApiKeyEntity> getMyKeys(Long userId) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		return externalApiKeyRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public ExternalApiKeyEntity update(
			Long userId,
			Long externalKeyId,
			ExternalApiKeyProvider provider,
			String alias,
			String plainKey
	) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		if (externalKeyId == null) {
			throw new IllegalArgumentException("externalKeyId는 필수입니다");
		}
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다");
		}
		String trimmedAlias = StringUtils.hasText(alias) ? alias.trim() : "";
		if (!StringUtils.hasText(trimmedAlias)) {
			throw new IllegalArgumentException("alias는 필수입니다");
		}
		String normalizedKey = StringUtils.hasText(plainKey) ? plainKey.trim() : "";
		if (!StringUtils.hasText(normalizedKey)) {
			throw new IllegalArgumentException("externalKey는 필수입니다");
		}

		ExternalApiKeyEntity entity = externalApiKeyRepository.findByIdAndUserId(externalKeyId, userId)
				.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));

		String keyHash = encryptionUtil.sha256HexForUniqueness(provider.name(), normalizedKey);
		if (externalApiKeyRepository.existsByUserIdAndProviderAndKeyHashAndIdNot(userId, provider, keyHash, externalKeyId)) {
			throw new DuplicateExternalApiKeyException("이미 등록된 API 키입니다");
		}

		String encrypted = encryptionUtil.encryptAes256Gcm(normalizedKey);
		entity.updateCredential(provider, trimmedAlias, keyHash, encrypted);

		log.info(
				"[AUDIT] external_api_key_updated userId={} provider={} alias={} keyId={}",
				userId,
				provider.name(),
				trimmedAlias,
				entity.getId()
		);

		return entity;
	}

	@Transactional(readOnly = true)
	public InternalApiKeyResponse resolveInternalKey(Long userId, ExternalApiKeyProvider provider) {
		if (userId == null) {
			throw new IllegalArgumentException("userId는 필수입니다");
		}
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다");
		}
		ExternalApiKeyEntity entity = externalApiKeyRepository
				.findTopByUserIdAndProviderOrderByCreatedAtDesc(userId, provider)
				.orElseThrow(() -> new ExternalApiKeyNotFoundException("등록된 API 키를 찾을 수 없습니다"));
		String plainKey = encryptionUtil.decryptAes256Gcm(entity.getEncryptedKey());
		return new InternalApiKeyResponse(plainKey, String.valueOf(entity.getId()));
	}
}
